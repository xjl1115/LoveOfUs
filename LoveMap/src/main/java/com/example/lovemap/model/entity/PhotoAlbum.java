package com.example.lovemap.model.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 照片-相册关联实体类
 * 对应数据库表：photo_album
 */
@Data
public class PhotoAlbum {

    /**
     * 主键
     */
    private Long id;

    /**
     * 照片ID
     */
    private Long photoId;

    /**
     * 相册ID
     */
    private Long albumId;

    /**
     * 在相册内排序
     */
    private Integer sortOrder;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}
