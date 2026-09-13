package com.example.lovemap.model.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 约会计划状态变更 DTO
 */
@Data
public class DatePlanStatusDTO {

    @NotNull(message = "状态不能为空")
    private Integer status;
}
