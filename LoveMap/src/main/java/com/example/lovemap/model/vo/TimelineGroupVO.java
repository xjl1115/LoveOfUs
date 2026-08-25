package com.example.lovemap.model.vo;

import lombok.Data;

import java.util.List;

/**
 * 时间线分组 VO（按月）
 */
@Data
public class TimelineGroupVO {

    /**
     * 月份，格式：yyyy-MM
     */
    private String date;

    /**
     * 该月的照片列表
     */
    private List<TimelinePhotoVO> photos;
}