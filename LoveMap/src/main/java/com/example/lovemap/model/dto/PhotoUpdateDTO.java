package com.example.lovemap.model.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * 照片更新请求DTO
 */
@Data
public class PhotoUpdateDTO {

    /**
     * 照片ID
     */
    private Long id;

    /**
     * 拍摄日期
     */
    private LocalDate takenDate;

    /**
     * 拍摄时间
     */
    private LocalTime takenTime;

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
     * 手动标签列表
     */
    private List<String> tags;
}
