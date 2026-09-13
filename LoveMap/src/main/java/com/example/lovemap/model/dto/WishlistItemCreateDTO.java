package com.example.lovemap.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * 创建心愿清单项 DTO
 */
@Data
public class WishlistItemCreateDTO {

    @NotBlank(message = "心愿标题不能为空")
    @Size(max = 255, message = "心愿标题最多255字符")
    private String title;

    @Size(max = 1000, message = "描述最多1000字符")
    private String description;

    private Integer priority;

    @Size(max = 50, message = "分类最多50字符")
    private String category;

    @Size(max = 50, message = "图标最多50字符")
    private String icon;

    private Integer targetValue;

    @Size(max = 20, message = "单位最多20字符")
    private String unit;

    private LocalDate targetDate;

    private Integer needBothConfirm;
}
