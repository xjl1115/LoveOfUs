package com.example.lovemap.model.vo;

import lombok.Data;

/**
 * 事项关联图片 VO
 */
@Data
public class ThingsPhotoVO {

    /**
     * 图片 URL ID
     */
    private Long id;

    /**
     * 图片 URL
     */
    private String url;
}