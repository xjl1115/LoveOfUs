package com.example.lovemap.chat;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 聊天 WebSocket Session 注册表
 * <p>
 * 维护 online userId → WebSocketSession 的映射。
 * 一个用户可能多端登录（PC + 移动），这里存多个 Session。
 */
@Slf4j
@Component
public class ChatSessionRegistry {

    /** userId → 该用户的所有活跃 Session（多端） */
    private final Map<Integer, Set<WebSocketSession>> sessions = new ConcurrentHashMap<>();

    /**
     * 注册会话
     */
    public void register(Integer userId, WebSocketSession session) {
        sessions.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(session);
        log.info("聊天会话注册, userId: {}, sessionId: {}, 当前在线: {} 人", userId, session.getId(), sessions.size());
    }

    /**
     * 注销会话
     */
    public void unregister(Integer userId, WebSocketSession session) {
        Set<WebSocketSession> set = sessions.get(userId);
        if (set != null) {
            set.remove(session);
            if (set.isEmpty()) {
                sessions.remove(userId);
            }
        }
    }

    /**
     * 获取某用户的所有活跃 Session
     */
    public Collection<WebSocketSession> getSessions(Integer userId) {
        Set<WebSocketSession> set = sessions.get(userId);
        return set == null ? Collections.emptyList() : Collections.unmodifiableCollection(set);
    }

    /**
     * 判断用户是否在线
     */
    public boolean isOnline(Integer userId) {
        Set<WebSocketSession> set = sessions.get(userId);
        return set != null && !set.isEmpty();
    }

    /**
     * 主动向某用户的所有 Session 推送文本
     */
    public void sendTo(Integer userId, String text) {
        Collection<WebSocketSession> userSessions = getSessions(userId);
        for (WebSocketSession s : userSessions) {
            try {
                if (s.isOpen()) {
                    synchronized (s) {
                        s.sendMessage(new org.springframework.web.socket.TextMessage(text));
                    }
                }
            } catch (IOException e) {
                log.warn("聊天消息发送失败, userId: {}, sessionId: {}", userId, s.getId(), e);
            }
        }
    }

    public int onlineCount() {
        return sessions.size();
    }
}
