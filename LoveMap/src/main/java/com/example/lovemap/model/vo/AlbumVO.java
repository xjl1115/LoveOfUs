package com.example.lovemap.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 相册VO
 */
@Data
public class AlbumVO {

    /**
     * 相册ID
     */
    private Long id;

    /**
     * 情侣组ID
     */
    private Long groupId;

    /**
     * 相册名称
     */
    private String name;

    /**
     * 相册描述
     */
    private String description;

    /**
     * 封面照片URL（API 规范字段名，与 coverUrl 值相同）
     */
    private String coverPhotoUrl;

    /**
     * 照片数量
     */
    private Integer photoCount;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
