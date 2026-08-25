package com.example.lovemap.model.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * 照片列表VO
 */
@Data
public class PhotoVO {

    /**
     * 照片ID
     */
    private Long id;

    /**
     * 上传用户ID
     */
    private Long userId;

    /**
     * 缩略图URL
     */
    private String thumbnailUrl;

    /**
     * 中等尺寸图片URL
     */
    private String mediumUrl;

    /**
     * 原图URL
     */
    private String originalUrl;

    /**
     * 文件大小（字节）
     */
    private Long fileSize;

    /**
     * 图片宽度
     */
    private Integer width;

    /**
     * 图片高度
     */
    private Integer height;

    /**
     * 拍摄日期
     */
    private LocalDate takenDate;

    /**
     * 拍摄时间
     */
    private LocalTime takenTime;

    /**
     * GPS纬度
     */
    private BigDecimal latitude;

    /**
     * GPS经度
     */
    private BigDecimal longitude;

    /**
     * 地点名称
     */
    private String locationName;

    /**
     * 城市
     */
    private String city;

    /**
     * 省份
     */
    private String province;

    /**
     * 国家
     */
    private String country;

    /**
     * 用户描述
     */
    private String description;

    /**
     * AI生成的标签
     */
    private List<String> aiTags;

    /**
     * AI情绪分析
     */
    private String aiEmotion;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}
