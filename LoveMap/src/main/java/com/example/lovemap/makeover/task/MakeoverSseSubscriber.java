package com.example.lovemap.makeover.task;

import com.example.lovemap.makeover.constant.MakeoverConstant;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.Topic;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * AI 化妆建议 SSE 订阅器
 * <p>
 * 作为独立 Spring 组件启动，订阅 Redis Pub/Sub 频道
 * "makeover:stream:*"，将事件分发到对应 recordId 的 SseEmitter 列表。
 * <p>
 * 注册 emitter 与移除 emitter 由 MakeoverController 直接调用本组件，
 * 避免 Controller 反向依赖 Subscriber 的内部结构。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MakeoverSseSubscriber implements MessageListener {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private RedisMessageListenerContainer container;

    /**
     * recordId -> emitters
     */
    private final Map<Long, List<SseEmitter>> emitterMap = new ConcurrentHashMap<>();

    @PostConstruct
    public void start() {
        container = new RedisMessageListenerContainer();
        container.setConnectionFactory(redisTemplate.getRequiredConnectionFactory());
        Topic topic = new PatternTopic("makeover:stream:*");
        container.addMessageListener(this, topic);
        container.afterPropertiesSet();
        container.start();
        log.info("[Makeover-SSE] Redis Pub/Sub 订阅已启动, topic=makeover:stream:*");
    }

    @PreDestroy
    public void stop() {
        if (container != null) {
            container.stop();
        }
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String channel = new String(message.getChannel(), StandardCharsets.UTF_8);
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        try {
            String suffix = channel.substring("makeover:stream:".length());
            Long recordId = Long.parseLong(suffix);
            dispatch(recordId, body);
        } catch (Exception e) {
            log.warn("[Makeover-SSE] 消息分发异常 channel={}: {}", channel, e.getMessage());
        }
    }

    private void dispatch(Long recordId, String jsonBody) {
        Map<String, Object> envelope;
        try {
            envelope = objectMapper.readValue(jsonBody, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("[Makeover-SSE] 消息 JSON 解析失败: {}", e.getMessage());
            return;
        }
        String event = String.valueOf(envelope.get("event"));
        Object data = envelope.get("data");

        List<SseEmitter> emitters = emitterMap.get(recordId);
        if (emitters == null || emitters.isEmpty()) return;

        for (SseEmitter emitter : emitters) {
            try {
                // SseEmitter 对非字符串对象会按 text/event-stream 查找 HttpMessageConverter，
                // 找不到会抛 HttpMessageNotWritableException 导致事件无法送达；
                // 这里统一把 data 预序列化为 JSON 字符串（与前端 JSON.parse 约定一致）。
                String payload = (data instanceof String s) ? s : objectMapper.writeValueAsString(data);
                emitter.send(SseEmitter.event().name(event).data(payload));
                if (MakeoverConstant.SSE_EVENT_DONE.equals(event)
                        || MakeoverConstant.SSE_EVENT_ERROR.equals(event)) {
                    emitter.complete();
                }
            } catch (Exception e) {
                log.debug("[Makeover-SSE] 推送失败 recordId={}: {}", recordId, e.getMessage());
            }
        }
    }

    /**
     * 注册 SSE 连接
     */
    public void register(Long recordId, SseEmitter emitter) {
        emitterMap.computeIfAbsent(recordId, k -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> remove(recordId, emitter));
        emitter.onTimeout(() -> remove(recordId, emitter));
        emitter.onError(e -> remove(recordId, emitter));
    }

    private void remove(Long recordId, SseEmitter emitter) {
        List<SseEmitter> list = emitterMap.get(recordId);
        if (list != null) list.remove(emitter);
    }
}
