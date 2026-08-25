package com.example.lovemap.model.vo;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 时间线VO（按日期分组）
 */
@Data
public class TimelineVO {

    /**
     * 日期
     */
    private LocalDate date;

    /**
     * 该日期的照片列表
     */
    private List<PhotoVO> photos;

    /**
     * 该日期照片数量
     */
    private Integer count;
}
