package com.example.lovemap.model.vo;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 用户统计数据VO
 */
@Data
public class UserStatsVO {

    /**
     * 照片数量
     */
    private Integer photoCount;

    /**
     * 相册数量
     */
    private Integer albumCount;

    /**
     * 去过城市数量
     */
    private Integer cityCount;

    /**
     * 在一起的天数
     */
    private Integer daysTogether;

    /**
     * 各省份照片数量统计
     */
    private List<ProvinceStatVO> cities;

    /**
     * 省份统计VO
     */
    @Data
    public static class ProvinceStatVO {

        /**
         * 省份名称
         */
        private String name;

        /**
         * 照片数量
         */
        private Integer count;

        /**
         * 最新的照片拍摄日期
         */
        private LocalDate takenDate;
    }
}
