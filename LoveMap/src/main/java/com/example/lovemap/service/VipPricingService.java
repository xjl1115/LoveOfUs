package com.example.lovemap.service;

import com.example.lovemap.model.entity.VipPricing;

import java.math.BigDecimal;
import java.util.List;

/**
 * VIP 与次卡价格服务
 */
public interface VipPricingService {

    /**
     * 列出全部价格，按展示顺序升序
     */
    List<VipPricing> listAll();

    /**
     * 按业务编码查询；缓存或数据库中无记录时返回 null
     */
    VipPricing findByCode(String productCode);

    /**
     * 按 VIP 档位查价格；不存在返回 null
     */
    BigDecimal getVipTierPrice(int vipLevel);

    /**
     * 按次卡类型查单价；不存在返回 null
     */
    BigDecimal getQuotaUnitPrice(int quotaType);

    /**
     * 批量更新价格并失效缓存。
     *
     * @param updates 每个元素为 (id, newPrice)
     * @return 受影响的行数
     */
    int updatePrices(List<PriceUpdate> updates);

    /**
     * 单条价格更新
     *
     * @param id        主键
     * @param priceYuan 新单价
     */
    record PriceUpdate(Long id, BigDecimal priceYuan) {
    }
}
