package com.example.lovemap.ai.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 会话列表项（不含消息详情）
 */
@Data
public class AiSessionSummaryVO {

    private String sessionId;
    private String title;
    private Integer messageCount;
    private Integer pinned;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm")
    private LocalDateTime lastActiveAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm")
    private LocalDateTime createdAt;
}