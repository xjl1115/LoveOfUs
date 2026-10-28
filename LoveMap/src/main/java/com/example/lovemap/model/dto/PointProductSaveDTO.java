package com.example.lovemap.model.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 顾问控制台：积分商品保存 DTO
 * 用于新建与更新；id 为 null 时表示新建
 */
@Data
public class PointProductSaveDTO {

    private Long id;

    @NotBlank(message = "商品名称不能为空")
    @Size(max = 64, message = "商品名称长度不能超过 64")
    private String name;

    @Size(max = 500, message = "商品描述长度不能超过 500")
    private String description;

    @NotNull(message = "兑换积分不能为空")
    @Min(value = 1, message = "兑换积分必须大于 0")
    private Integer pointsCost;

    /** 需付款金额（元），为空表示纯积分兑换 */
    @DecimalMin(value = "0.00", message = "金额不能为负")
    private BigDecimal priceAmount;

    /** 促销：0=关 1=开 */
    private Integer promoStatus;

    /** 促销积分，促销开启时必填 */
    @Min(value = 1, message = "促销积分必须大于 0")
    private Integer promoPoints;

    /** 促销金额（元），促销开启时必填 */
    @DecimalMin(value = "0.00", message = "促销金额不能为负")
    private BigDecimal promoAmount;

    /** 库存，-1 表示不限 */
    @NotNull(message = "库存不能为空")
    private Integer stock;

    @Size(max = 500, message = "图片 URL 长度不能超过 500")
    private String imageUrl;

    /** 0=下架 1=上架 */
    @NotNull(message = "状态不能为空")
    private Integer status;

    /** 展示顺序 */
    private Integer sortOrder;
}
