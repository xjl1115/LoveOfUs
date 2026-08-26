package com.example.lovemap.model.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 聊天会话实体
 */
@Data
public class AiChatSession {

    private Long id;
    private String sessionId;
    private Long userId;
    private String title;
    private Integer pinned;
    private Integer messageCount;
    private LocalDateTime lastActiveAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer isDeleted;
}