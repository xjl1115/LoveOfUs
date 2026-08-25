package com.example.lovemap.chat;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 聊天页面在线状态注册表
 * <p>
 * 记录"用户当前是否停留在与某位伴侣的聊天页面"，用于：
 *  - 接收方在聊天页时，对方发来的新消息直接标记已读
 *  - 通过 SSE 实时推送已读回执给发送方，发送方立刻看到"已读"
 * <p>
 * TTL 设计：30 秒。心跳由前端每 20 秒调用一次刷新；定时清理任务每 60 秒扫描过期项。
 */
@Slf4j
@Component
public class ChatPresenceRegistry {

    /** 用户进入聊天页的空闲过期时间（毫秒） */
    private static final long TTL_MS = 30_000L;

    /**
     * userId -> (partnerId, 最后心跳时间戳)
     */
    private final ConcurrentHashMap<Integer, Entry> presenceMap = new ConcurrentHashMap<>();

    /**
     * 进入聊天页（首次或心跳刷新）。
     *
     * @param userId    当前用户
     * @param partnerId 对话的伴侣 ID
     */
    public void enter(Integer userId, Integer partnerId) {
        if (userId == null || partnerId == null) return;
        presenceMap.put(userId, new Entry(partnerId, System.currentTimeMillis()));
    }

    /**
     * 心跳刷新（仅刷新时间戳，不切换 partnerId）
     */
    public void heartbeat(Integer userId) {
        Entry e = presenceMap.get(userId);
        if (e == null) return;
        e.lastUpdateMs = System.currentTimeMillis();
    }

    /**
     * 离开聊天页
     */
    public void leave(Integer userId) {
        if (userId == null) return;
        presenceMap.remove(userId);
    }

    /**
     * 判断 userId 当前是否停留在与 partnerId 的聊天页（且未过期）
     */
    public boolean isInChatWith(Integer userId, Integer partnerId) {
        if (userId == null || partnerId == null) return false;
        Entry e = presenceMap.get(userId);
        if (e == null) return false;
        if (!e.partnerId.equals(partnerId)) return false;
        return (System.currentTimeMillis() - e.lastUpdateMs) < TTL_MS;
    }

    /**
     * 清理过期项（定时任务调用）
     */
    public void cleanupExpired() {
        long now = System.currentTimeMillis();
        int removed = 0;
        for (var it = presenceMap.entrySet().iterator(); it.hasNext();) {
            var en = it.next().getValue();
            if ((now - en.lastUpdateMs) >= TTL_MS) {
                it.remove();
                removed++;
            }
        }
        if (removed > 0) {
            log.debug("聊天在线状态清理, 移除过期: {}", removed);
        }
    }

    private static final class Entry {
        final Integer partnerId;
        volatile long lastUpdateMs;

        Entry(Integer partnerId, long lastUpdateMs) {
            this.partnerId = partnerId;
            this.lastUpdateMs = lastUpdateMs;
        }
    }
}