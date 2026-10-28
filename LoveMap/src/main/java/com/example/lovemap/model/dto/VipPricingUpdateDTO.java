package com.example.lovemap.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 顾问端批量更新 VIP/次卡价格请求 DTO
 */
@Data
public class VipPricingUpdateDTO {

    /**
     * 价格更新项列表，至少 1 条
     */
    @NotEmpty(message = "至少提交一条价格变更")
    @Valid
    private List<Item> items;

    /**
     * 单条更新：主键 + 新价格
     */
    @Data
    public static class Item {
        /** 主键，必填 */
        @jakarta.validation.constraints.NotNull(message = "价格项 id 不能为空")
        private Long id;

        /** 新单价，必须 > 0 */
        @DecimalMin(value = "0.00", inclusive = false, message = "价格必须大于 0")
        private BigDecimal priceYuan;
    }
}
