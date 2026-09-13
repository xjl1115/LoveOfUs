package com.example.lovemap.model.vo;

import lombok.Data;

/**
 * 情侣必做 100 件事统计 VO
 */
@Data
public class ThingsStatsVO {

    /**
     * 总数（100）
     */
    private Long total;

    /**
     * 已完成数
     */
    private Long achieved;

    /**
     * 完成率 0-1
     */
    private Double rate;
}