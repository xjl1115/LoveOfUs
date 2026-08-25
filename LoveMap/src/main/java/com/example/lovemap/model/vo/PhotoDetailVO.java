package com.example.lovemap.model.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 照片详情 VO
 */
@Data
public class PhotoDetailVO {

    /**
     * 照片ID
     */
    private Long id;

    /**
     * 存储路径（原图）
     */
    private String storagePath;

    /**
     * 拍摄日期
     */
    private String takenDate;

    /**
     * 地点名称
     */
    private String locationName;

    /**
     * 国家
     */
    private String country;

    /**
     * 城市
     */
    private String city;

    /**
     * 省份
     */
    private String province;

    /**
     * 描述
     */
    private String description;

    /**
     * 上传者信息
     */
    private UploaderInfo uploader;

    /**
     * 所属相册列表
     */
    private List<AlbumInfo> albums;

    /**
     * 上一张照片ID（按拍摄时间降序排列中，比当前更新的那张）
     */
    private Long prevPhotoId;

    /**
     * 下一张照片ID（按拍摄时间降序排列中，比当前更旧的那张）
     */
    private Long nextPhotoId;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    @Data
    public static class UploaderInfo {
        private Long id;
        private String nickname;
    }

    @Data
    public static class AlbumInfo {
        private Long id;
        private String name;
    }
}