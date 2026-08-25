package com.example.lovemap.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * AI 聊天响应（单轮回复）
 * 与前端 ChatMessage 类型对齐
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse implements Serializable {

    /** 会话 id（回传给前端，便于前端归类） */
    private String sessionId;

    /** AI 回复的消息 */
    private ChatMessage message;

    /** 单条消息结构 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChatMessage implements Serializable {
        /** 消息 id（前端生成；后端返回时间戳 id 即可） */
        private String id;
        /** 角色：固定 "ai" */
        private String role;
        /** 消息文本 */
        private String content;
        /** 创建时间（毫秒） */
        private Long createdAt;
    }
}