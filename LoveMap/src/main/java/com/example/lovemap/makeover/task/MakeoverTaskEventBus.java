package com.example.lovemap.makeover.task;

import com.example.lovemap.makeover.constant.MakeoverConstant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AI 化妆建议 SSE 事件总线
 * <p>
 * 借助 Redis Pub/Sub：异步任务线程 publish，
 * 由 SSE 连接端订阅（见 MakeoverController#stream）。
 * <p>
 * 设计取舍：
 * 1. 任务在 @Async 线程执行，SSE 连接在 Controller 线程，需要跨线程通信；
 * 2. 使用 Redis Pub/Sub 是项目现有模式（NotificationService），
 *    同时支持未来横向扩展（多实例也能跨节点推送）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MakeoverTaskEventBus {

    private final StringRedisTemplate redisTemplate;

    public void pushStage(Long recordId, String stage, String status) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("stage", stage);
        payload.put("status", status);
        publish(recordId, MakeoverConstant.SSE_EVENT_STAGE, payload);
    }

    public void pushDone(Long recordId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("recordId", recordId);
        publish(recordId, MakeoverConstant.SSE_EVENT_DONE, payload);
    }

    public void pushError(Long recordId, String message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("message", message);
        publish(recordId, MakeoverConstant.SSE_EVENT_ERROR, payload);
    }

    private void publish(Long recordId, String event, Object payload) {
        String channel = MakeoverConstant.sseChannel(recordId);
        try {
            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("event", event);
            envelope.put("data", payload);
            String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(envelope);
            redisTemplate.convertAndSend(channel, json);
        } catch (Exception e) {
            log.warn("[Makeover-SSE] 推送事件失败 channel={}, event={}, err={}",
                    channel, event, e.getMessage());
        }
    }
}