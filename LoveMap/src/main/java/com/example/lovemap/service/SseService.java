package com.example.lovemap.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.Set;
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
     * key: userId, value: 该用户当前所有活跃连接
     * <p>
     * 同一账号允许多条连接共存（多窗口 / 多设备各自独立）：
     * 旧实现一个用户只保留一条连接，新连接会把旧连接 complete 掉，
     * 两端会互相抢通道，导致未读数、已读回执等 SSE 事件被静默丢弃。
     */
    private final Map<Integer, Set<SseEmitter>> emitterMap = new ConcurrentHashMap<>();

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

        // 创建新的 SseEmitter，设置超时时间
        SseEmitter emitter = new SseEmitter(sseTimeout);

        // 回调按"连接实例"移除，避免旧连接的回调误删同一用户的新连接
        emitter.onCompletion(() -> {
            log.debug("SSE连接完成, userId: {}", userId);
            removeConnection(userId, emitter);
        });

        emitter.onTimeout(() -> {
            log.debug("SSE连接超时, userId: {}", userId);
            removeConnection(userId, emitter);
        });

        emitter.onError((e) -> {
            log.warn("SSE连接错误, userId: {}, error: {}", userId, e.getMessage());
            removeConnection(userId, emitter);
        });

        // 保存连接
        Set<SseEmitter> connections = emitterMap.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet());
        connections.add(emitter);
        log.info("SSE连接创建成功, userId: {}, 该用户连接数: {}, 有连接用户数: {}",
                userId, connections.size(), emitterMap.size());

        return emitter;
    }

    /**
     * 移除单条连接；仅当该用户已无任何连接时才删除用户条目
     */
    private void removeConnection(Integer userId, SseEmitter emitter) {
        Set<SseEmitter> connections = emitterMap.get(userId);
        if (connections == null) {
            return;
        }
        connections.remove(emitter);
        if (connections.isEmpty()) {
            emitterMap.remove(userId, connections);
        }
    }

    /**
     * 关闭指定用户的全部 SSE 连接（登出/踢下线时使用）
     *
     * @param userId 用户ID
     */
    public void closeConnection(Integer userId) {
        if (userId == null) {
            return;
        }
        Set<SseEmitter> connections = emitterMap.remove(userId);
        if (connections == null) {
            return;
        }
        for (SseEmitter emitter : connections) {
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
        Set<SseEmitter> connections = emitterMap.get(userId);
        if (connections == null || connections.isEmpty()) {
            log.debug("用户SSE连接不存在, userId: {}, event: {}", userId, eventName);
            return;
        }

        // 广播给该用户的所有连接；失败的连接单独摘除，不影响其余连接
        for (SseEmitter emitter : connections) {
            try {
                emitter.send(SseEmitter.event()
                        .name(eventName)
                        .data(data));
                log.debug("SSE事件推送成功, userId: {}, event: {}", userId, eventName);
            } catch (Exception e) {
                log.warn("SSE事件推送失败, userId: {}, event: {}, error: {}", userId, eventName, e.getMessage());
                removeConnection(userId, emitter);
            }
        }
    }

    /**
     * 获取当前有 SSE 连接的用户数
     *
     * @return 用户数
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
