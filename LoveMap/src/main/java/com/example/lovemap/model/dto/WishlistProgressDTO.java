package com.example.lovemap.model.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 心愿进度调整 DTO（+1 / -1，服务端原子增减并自动限制在 [0, targetValue]）
 */
@Data
public class WishlistProgressDTO {

    @NotNull(message = "调整值不能为空")
    private Integer delta;
}
