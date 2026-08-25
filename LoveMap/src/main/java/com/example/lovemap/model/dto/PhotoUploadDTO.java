package com.example.lovemap.model.dto;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 照片上传 DTO
 */
@Data
public class PhotoUploadDTO {

    /**
     * 照片文件（最多 20 张）
     */
    private List<MultipartFile> files;

    /**
     * 拍摄日期，格式：yyyy-MM-dd
     */
    private String takenDate;

    /**
     * 国家名称
     */
    private String country;

    /**
     * 省份名称
     */
    private String province;

    /**
     * 城市名称
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

    /**
     * 相册ID
     */
    private Long albumId;
}