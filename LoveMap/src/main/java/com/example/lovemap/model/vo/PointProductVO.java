package com.example.lovemap.model.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 积分商品 VO
 */
@Data
public class PointProductVO {

    private Long id;
    private String name;
    private String description;
    private Integer pointsCost;
    /** 需付款金额（元），NULL=仅积分兑换 */
    private BigDecimal priceAmount;
    /** 促销：0=关 1=开 */
    private Integer promoStatus;
    /** 促销积分 */
    private Integer promoPoints;
    /** 促销金额（元） */
    private BigDecimal promoAmount;
    /** 库存，-1 表示不限 */
    private Integer stock;
    /** 是否库存充足（前端兑换按钮可用性） */
    private Boolean inStock;
    private String imageUrl;
    private Integer status;
    private Integer sortOrder;
    private LocalDateTime updatedAt;
}
