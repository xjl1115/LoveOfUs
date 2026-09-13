package com.example.lovemap.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 化妆建议列表项
 */
@Data
public class MakeoverListVO {

    private Long recordId;
    private String sceneCode;
    private String sceneText;
    private String originalUrl;
    private String afterUrl;
    private Byte status;
    private LocalDateTime createdAt;
}