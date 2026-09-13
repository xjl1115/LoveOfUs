package com.example.lovemap.model.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 更新约会计划 DTO
 */
@Data
public class DatePlanUpdateDTO {

    @Size(max = 255, message = "约会标题最多255字符")
    private String title;

    private LocalDate planDate;

    @Size(max = 255, message = "地点最多255字符")
    private String location;

    @Size(max = 50, message = "时段最多50字符")
    private String timeSlot;

    private List<String> scenes;

    @Size(max = 500, message = "地点建议最多500字符")
    private String locationSuggestion;

    @Size(max = 20, message = "预算最多20字符")
    private String budget;

    @Size(max = 2000, message = "推荐理由最多2000字符")
    private String reason;

    private List<String> tips;

    @Size(max = 1000, message = "描述最多1000字符")
    private String description;
}
