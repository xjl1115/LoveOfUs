package com.example.lovemap.ai.vo;

import lombok.Data;

import java.util.List;

/**
 * AI 会话详情（含消息列表）
 */
@Data
public class AiSessionDetailVO {

    private String sessionId;
    private String title;
    private Integer pinned;
    private List<AiMessageVO> messages;
}