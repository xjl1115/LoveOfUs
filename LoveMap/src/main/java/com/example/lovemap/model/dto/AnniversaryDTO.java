package com.example.lovemap.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * 纪念日请求 DTO
 */
@Data
public class AnniversaryDTO {

    @NotBlank(message = "纪念日名称不能为空")
    private String name;

    @NotNull(message = "纪念日日期不能为空")
    private LocalDate anniversaryDate;

    private Boolean isRecurring;

    private Integer remindDays;

    @Size(max = 200, message = "备注描述最多200字符")
    private String description;
}