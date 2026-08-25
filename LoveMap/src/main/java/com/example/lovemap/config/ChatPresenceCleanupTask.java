package com.example.lovemap.config;

import com.example.lovemap.chat.ChatPresenceRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 聊天页面在线状态清理定时任务
 * 每 60 秒清理一次心跳过期的记录
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatPresenceCleanupTask {

    private final ChatPresenceRegistry chatPresenceRegistry;

    @Scheduled(fixedDelay = 60_000L)
    public void cleanup() {
        chatPresenceRegistry.cleanupExpired();
    }
}