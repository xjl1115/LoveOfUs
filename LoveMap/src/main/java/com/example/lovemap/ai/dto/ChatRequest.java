package com.example.lovemap.ai.dto;

import lombok.Data;

/**
 * AI 聊天请求体
 */
@Data
public class ChatRequest {

    /**
     * 会话 id（前端 localStorage 生成）
     */
    private String sessionId;

    /**
     * 用户输入的文本
     */
    private String message;
}