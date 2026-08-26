package com.example.lovemap.ai.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 会话消息详情
 */
@Data
public class AiMessageVO {

    private String id;
    private String role;
    private String content;
    private String toolName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}