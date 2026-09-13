package com.example.lovemap.model.entity;

import lombok.Data;

/**
 * 情侣必做 100 件事实体
 * 对应数据库表：things
 *
 * 表结构（保持现状，不做改动）：
 *   id          int PK
 *   name        varchar(255)
 *   description text
 *   completed   int default 0（保留字段，不再使用）
 *
 * 注：实际"谁完成了"的标记存于 things_completion 表，此处 completed 字段保持作为冗余/兜底。
 */
@Data
public class Things {

    /**
     * 主键
     */
    private Long id;

    /**
     * 事项名称（如：一起看一次日出）
     */
    private String name;

    /**
     * 事项描述
     */
    private String description;

    /**
     * 左侧图标（emoji 文本，如 🌅）
     */
    private String icon;

    /**
     * 完成标记 0-未完成 1-已完成（保留字段，新逻辑以 things_completion 为准）
     */
    private Integer completed;
}