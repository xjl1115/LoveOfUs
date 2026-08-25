package com.example.lovemap.model.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 相册实体类
 * 对应数据库表：album
 */
@Data
public class Album {

    /**
     * 主键
     */
    private Long id;

    /**
     * 情侣组ID
     */
    private Long groupId;

    /**
     * 用户ID（未绑定时使用）
     */
    private Long userId;

    /**
     * 相册名称
     */
    private String name;

    /**
     * 相册描述
     */
    private String description;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
