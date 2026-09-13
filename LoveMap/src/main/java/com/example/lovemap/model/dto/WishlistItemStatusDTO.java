package com.example.lovemap.model.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 心愿清单状态变更 DTO
 */
@Data
public class WishlistItemStatusDTO {

    @NotNull(message = "状态不能为空")
    private Integer status;

    @Size(max = 1000, message = "达成笔记最多1000字符")
    private String note;
}
