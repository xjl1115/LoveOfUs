package com.example.lovemap.model.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 相册详情VO（含照片列表）
 */
@Data
public class AlbumDetailVO {

    /**
     * 相册ID
     */
    private Long id;

    /**
     * 相册名称
     */
    private String name;

    /**
     * 相册描述
     */
    private String description;

    /**
     * 封面照片URL
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

    /**
     * 照片列表
     */
    private List<PhotoInAlbumVO> photos;

    /**
     * 分页信息
     */
    private PageVO page;

    @Data
    public static class PageVO {
        /**
         * 当前页码，从1开始
         */
        private Integer current;
        /**
         * 每页大小
         */
        private Integer size;
        /**
         * 总记录数
         */
        private Long total;
        /**
         * 总页数
         */
        private Integer pages;
    }

    @Data
    public static class PhotoInAlbumVO {
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
         * 照片描述
         */
        private String description;

        /**
         * 上传用户ID
         */
        private Long userId;

        /**
         * 用户昵称
         */
        private String userNickname;
    }
}
