package com.example.lovemap.model.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 约会计划视图对象
 */
@Data
public class DatePlanVO {

    private Long id;

    private Long groupId;

    private String title;

    @JsonProperty("date")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate planDate;

    private String timeSlot;

    private List<String> scenes;

    private String location;

    private String locationSuggestion;

    private String budget;

    private String reason;

    private List<String> tips;

    private List<String> photos;

    private String description;

    private Integer status;

    private Long createdBy;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime updatedAt;
}
