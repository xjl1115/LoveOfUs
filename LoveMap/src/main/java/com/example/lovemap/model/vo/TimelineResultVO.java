package com.example.lovemap.model.vo;

import lombok.Data;

import java.util.List;

/**
 * 时间线结果 VO
 */
@Data
public class TimelineResultVO {

    /**
     * 总照片数
     */
    private Long total;

    /**
     * 当前页码
     */
    private Integer page;

    /**
     * 每页大小
     */
    private Integer size;

    /**
     * 按月分组的照片列表
     */
    private List<TimelineGroupVO> records;
}