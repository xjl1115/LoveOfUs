package com.example.lovemap.model.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 照片实体类
 * 对应数据库表：photo
 */
@Data
public class Photo {

    /**
     * 主键
     */
    private Long id;

    /**
     * 上传用户ID
     */
    private Long userId;

    /**
     * 群组id
     */
    private Long groupId;

    /**
     * MinIO存储路径
     */
    private String storagePath;

    /**
     * 拍摄日期
     */
    private LocalDate takenDate;

    /**
     * 地点名称（文字描述）
     */
    private String locationName;

    /**
     * 城市
     */
    private String city;

    /**
     * 省份ID（关联 provence 表）
     */
    private Integer province;

    /**
     * 国家
     */
    private String country;

    /**
     * 用户描述
     */
    private String description;

    /**
     * 软删除标记：0-未删除，1-已删除
     */
    private Integer isDeleted;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}
