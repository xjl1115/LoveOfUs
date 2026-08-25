package com.example.lovemap.model.dto;

import lombok.Data;

/**
 * 时间线查询DTO
 */
@Data
public class TimelineDTO {

    /**
     * 页码，默认1
     */
    private Integer page = 1;

    /**
     * 每页条数，默认20
     */
    private Integer size = 20;

    /**
     * 年份筛选（可选）
     */
    private Integer year;

    /**
     * 月份筛选（可选）
     */
    private Integer month;
}
