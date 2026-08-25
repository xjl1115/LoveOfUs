package com.example.lovemap.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SSE 服务
 * 管理 Server-Sent Events 连接和消息推送
 */
@Service
@Slf4j
public class SseService {

    /**
     * 用户 SSE 连接池
     * key: userId, value: SseEmitter
     */
    private final Map<Integer, SseEmitter> emitterMap = new ConcurrentHashMap<>();

    /**
     * SSE 超时时间（毫秒），从配置文件读取
     */
    @Value("${sse.timeout:1800000}")
    private long sseTimeout;

    /**
     * 创建 SSE 连接
     *
     * @param userId 用户ID
     * @return SseEmitter
     */
    public SseEmitter createConnection(Integer userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId cannot be null");
        }
        // 关闭旧连接
        closeConnection(userId);

        // 创建新的 SseEmitter，设置超时时间
        SseEmitter emitter = new SseEmitter(sseTimeout);

        // 设置回调
        emitter.onCompletion(() -> {
            log.debug("SSE连接完成, userId: {}", userId);
            emitterMap.remove(userId);
        });

        emitter.onTimeout(() -> {
            log.debug("SSE连接超时, userId: {}", userId);
            emitterMap.remove(userId);
        });

        emitter.onError((e) -> {
            log.warn("SSE连接错误, userId: {}, error: {}", userId, e.getMessage());
            emitterMap.remove(userId);
        });

        // 保存连接
        emitterMap.put(userId, emitter);
        log.info("SSE连接创建成功, userId: {}, 当前连接数: {}", userId, emitterMap.size());

        return emitter;
    }

    /**
     * 关闭指定用户的 SSE 连接
     *
     * @param userId 用户ID
     */
    public void closeConnection(Integer userId) {
        if (userId == null) {
            return;
        }
        SseEmitter emitter = emitterMap.remove(userId);
        if (emitter != null) {
            try {
                emitter.complete();
            } catch (Exception e) {
                log.warn("关闭SSE连接异常, userId: {}", userId, e);
            }
        }
    }

    /**
     * 向指定用户推送事件
     *
     * @param userId    目标用户ID
     * @param eventName 事件名称
     * @param data      事件数据
     */
    public void sendEvent(Integer userId, String eventName, Object data) {
        SseEmitter emitter = emitterMap.get(userId);
        if (emitter == null) {
            log.debug("用户SSE连接不存在, userId: {}, event: {}", userId, eventName);
            return;
        }

        try {
            emitter.send(SseEmitter.event()
                    .name(eventName)
                    .data(data));
            log.debug("SSE事件推送成功, userId: {}, event: {}", userId, eventName);
        } catch (IOException e) {
            log.warn("SSE事件推送失败, userId: {}, event: {}, error: {}", userId, eventName, e.getMessage());
            emitterMap.remove(userId);
        }
    }

    /**
     * 获取当前在线连接数
     *
     * @return 连接数
     */
    public int getOnlineCount() {
        return emitterMap.size();
    }

    /**
     * 推送通知给指定用户
     *
     * @param userId 用户ID
     * @param notification 通知对象
     */
    public void pushNotification(Integer userId, Object notification) {
        sendEvent(userId, "notification", notification);
    }
}
