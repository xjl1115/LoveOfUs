package com.example.lovemap.model.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 标签实体类
 * 对应数据库表：tag
 */
@Data
public class Tag {

    /**
     * 主键
     */
    private Long id;

    /**
     * 标签名称
     */
    private String name;

    /**
     * 标签类型：manual-手动，ai-AI生成
     */
    private String type;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}
