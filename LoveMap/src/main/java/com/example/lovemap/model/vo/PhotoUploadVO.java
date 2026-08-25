package com.example.lovemap.model.vo;

import lombok.Data;

import java.util.List;

/**
 * 照片上传结果 VO
 */
@Data
public class PhotoUploadVO {

    /**
     * 上传的照片ID列表
     */
    private List<Long> photoIds;

    /**
     * 上传成功数量
     */
    private Integer successCount;

    /**
     * 相册ID
     */
    private Long albumId;
}