package com.example.lovemap.model.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 照片-标签关联实体类
 * 对应数据库表：photo_tag
 */
@Data
public class PhotoTag {

    /**
     * 主键
     */
    private Long id;

    /**
     * 照片ID
     */
    private Long photoId;

    /**
     * 标签ID
     */
    private Long tagId;

    /**
     * 来源：manual-手动，ai-AI生成
     */
    private String source;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}
