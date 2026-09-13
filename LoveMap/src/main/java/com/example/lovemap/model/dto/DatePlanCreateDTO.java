package com.example.lovemap.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 创建约会计划 DTO
 */
@Data
public class DatePlanCreateDTO {

    @NotBlank(message = "约会标题不能为空")
    @Size(max = 255, message = "约会标题最多255字符")
    private String title;

    private LocalDate planDate;

    @Size(max = 32, message = "时段最多32字符")
    private String timeSlot;

    private List<String> scenes;

    @Size(max = 255, message = "地点最多255字符")
    private String location;

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
