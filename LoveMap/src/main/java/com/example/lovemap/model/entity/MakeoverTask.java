package com.example.lovemap.model.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 化妆建议异步任务实体
 * 对应数据库表：makeover_task
 */
@Data
public class MakeoverTask {

    private Long id;

    /**
     * 关联记录 ID
     */
    private Long recordId;

    /**
     * 阶段：analyze / image_edit
     */
    private String stage;

    /**
     * 0=待执行 1=执行中 2=成功 3=失败
     */
    private Byte status;

    private Integer retryCount;

    private String errorMessage;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    private LocalDateTime createdAt;
}