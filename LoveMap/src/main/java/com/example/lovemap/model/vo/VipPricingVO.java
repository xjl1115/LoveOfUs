package com.example.lovemap.model.vo;

import com.example.lovemap.model.entity.VipPricing;
import lombok.Data;

import java.math.BigDecimal;

/**
 * VIP 价格项 VO（顾问端编辑 + 用户端展示通用）
 * <p>
 * 与 {@link VipPricing} 同语义，仅用于接口返回；字段顺序固定，避免依赖实体上的注解。
 */
@Data
public class VipPricingVO {

    /** 主键 */
    private Long id;

    /** 业务编码 */
    private String productCode;

    /** 产品名称 */
    private String productName;

    /** 产品类型：1=VIP 卡 2=次卡 */
    private Integer productType;

    /** VIP 档位（productType=1 时非空） */
    private Integer vipLevel;

    /** 次卡类型（productType=2 时非空） */
    private Integer quotaType;

    /** 单价（元） */
    private BigDecimal priceYuan;

    /** 产品描述 */
    private String description;

    /** 展示顺序 */
    private Integer sortOrder;
}
