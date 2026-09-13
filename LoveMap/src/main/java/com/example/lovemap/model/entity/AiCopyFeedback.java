package com.example.lovemap.model.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 纪念日文案反馈实体
 * 对应数据库表：ai_copy_feedback
 */
@Data
public class AiCopyFeedback {

    /**
     * 主键
     */
    private Long id;

    /**
     * 反馈用户 ID
     */
    private Long userId;

    /**
     * 关联纪念日 ID，可空（批量场景）
     */
    private Long anniversaryId;

    /**
     * 文案风格：romantic/casual/humor/poetic
     */
    private String style;

    /**
     * 与 Redis 缓存 key 后缀一致，便于关联
     */
    private String cacheKeySuffix;

    /**
     * 反馈类型：0-未反馈 1-采纳 2-拒绝
     */
    private Integer feedback;

    /**
     * 采纳 / 拒绝时的文案摘要（便于人工复核）
     */
    private String copyPreview;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}