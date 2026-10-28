package com.example.lovemap.model.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * VIP 与次卡价格
 * <p>
 * 同一张表存周/月/季/年/永久卡与三种次卡的单价，价格调整不再走代码。
 */
@Data
public class VipPricing {

    /** 主键 */
    private Long id;

    /** 业务编码，唯一：vip_week / vip_month / vip_quarter / vip_year / vip_forever / quota_makeover / quota_date_plan / quota_wishlist */
    private String productCode;

    /** 产品名称 */
    private String productName;

    /** 产品类型：1=VIP 卡 2=次卡 */
    private Integer productType;

    /** VIP 档位（productType=1 时非空）：1-周卡 2-月卡 3-季卡 4-年卡 5-永久 */
    private Integer vipLevel;

    /** 次卡类型（productType=2 时非空）：1=AI化妆 2=AI约会 3=AI心愿 */
    private Integer quotaType;

    /** 单价（元），DECIMAL(10,2) */
    private BigDecimal priceYuan;

    /** 产品描述 */
    private String description;

    /** 展示顺序：VIP 卡在前、次卡在后 */
    private Integer sortOrder;

    /** 最后更新时间 */
    private LocalDateTime updatedAt;
}
