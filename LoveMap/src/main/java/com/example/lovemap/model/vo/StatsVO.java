package com.example.lovemap.model.vo;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 统计数据VO
 */
@Data
public class StatsVO {

    /**
     * 照片总数
     */
    private Integer photoCount;

    /**
     * 相册总数
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
    private List<CityStatVO> cities;

    /**
     * 月度时间线统计
     */
    private List<MonthStatVO> monthlyTimeline;

    /**
     * 省份统计VO
     */
    @Data
    public static class CityStatVO {
        /**
         * 省份名称
         */
        private String name;

        /**
         * 照片数量
         */
        private Integer count;
    }

    /**
     * 月度统计VO
     */
    @Data
    public static class MonthStatVO {
        /**
         * 月份（格式：yyyy-MM）
         */
        private String month;

        /**
         * 照片数量
         */
        private Integer count;
    }
}
