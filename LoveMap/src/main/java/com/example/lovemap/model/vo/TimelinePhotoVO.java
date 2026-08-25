package com.example.lovemap.model.vo;

import lombok.Data;

/**
 * 时间线照片 VO
 */
@Data
public class TimelinePhotoVO {

    /**
     * 照片ID
     */
    private Long id;

    /**
     * 存储路径
     */
    private String storagePath;

    /**
     * 拍摄日期
     */
    private String takenDate;

    /**
     * 国家
     */
    private String country;

    /**
     * 省份名称
     */
    private String provence;

    /**
     * 城市
     */
    private String city;

    /**
     * 地点名称
     */
    private String locationName;

    /**
     * 描述
     */
    private String description;
}