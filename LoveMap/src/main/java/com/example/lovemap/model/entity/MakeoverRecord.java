package com.example.lovemap.model.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 化妆建议记录实体
 * 对应数据库表：makeover_record
 */
@Data
public class MakeoverRecord {

    /**
     * 主键
     */
    private Long id;

    /**
     * 所属用户
     */
    private Integer userId;

    /**
     * 对方用户（已绑定时冗余）
     */
    private Integer partnerId;

    /**
     * 原图 OSS 访问 URL
     */
    private String originalUrl;

    /**
     * OSS 对象键
     */
    private String originalKey;

    /**
     * 场景编码
     */
    private String sceneCode;

    /**
     * 场景自由文本
     */
    private String sceneText;

    /**
     * AI 返回的脸型/肤质特征 JSON
     */
    private String faceFeatures;

    /**
     * 妆造/配饰/服装建议 JSON
     */
    private String suggestions;

    /**
     * 改造总结 JSON：{ overall: "整体总结", steps: ["化妆步骤1", ...] }
     * 与 suggestions 同期由 AI 一次返回；详情页底部展示。
     */
    private String summary;

    /**
     * 改造效果图 OSS URL
     */
    private String afterUrl;

    /**
     * 改造图 OSS 对象键
     */
    private String afterKey;

    /**
     * 0=待处理 1=分析中 2=改造图生成中 3=已完成 4=失败 5=已取消
     */
    private Byte status;

    /**
     * 失败原因
     */
    private String errorMessage;

    /**
     * 总耗时（毫秒）
     */
    private Long costMs;

    /**
     * 软删除标记：0=未删 1=已删
     */
    private Byte deleted;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}