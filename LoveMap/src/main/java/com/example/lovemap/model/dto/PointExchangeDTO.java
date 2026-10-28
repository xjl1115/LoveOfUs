package com.example.lovemap.model.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 兑换商品请求 DTO
 */
@Data
public class PointExchangeDTO {

    /** 商品 ID */
    @NotNull(message = "商品 ID 不能为空")
    private Long productId;

    /** 兑换数量，默认 1 */
    @NotNull(message = "兑换数量不能为空")
    @Min(value = 1, message = "兑换数量必须大于 0")
    private Integer quantity;

    /** 收货地址 ID（含金额商品下单时必填） */
    private Long addressId;
}
