package com.example.lovemap.model.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 积分商品
 * 对应数据库表：point_product
 *
 * 设计要点：
 *   - 顾问控制台维护，前端只读
 *   - stock = -1 表示不限库存
 *   - status = 0 下架，前端不返回
 *   - priceAmount 为 NULL 表示纯积分兑换；有金额时需顾问线下收款后确认发放
 */
@Data
public class PointProduct {

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
    private String imageUrl;
    /** 0=下架 1=上架 */
    private Integer status;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
