package com.example.lovemap.model.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 聊天会话消息详情实体
 */
@Data
public class AiChatMessage {

    private Long id;
    private String sessionId;
    private Long userId;
    private String msgRole;
    private String msgContent;
    private String toolName;
    private Integer seq;
    private LocalDateTime createdAt;
}