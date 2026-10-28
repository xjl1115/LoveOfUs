package com.example.lovemap.mapper;

import com.example.lovemap.model.entity.VipPricing;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

/**
 * VIP 与次卡价格 Mapper
 */
@Mapper
public interface VipPricingMapper {

    /** 列出全部价格，按 sort_order 升序、id 升序 */
    List<VipPricing> selectAll();

    /** 按业务编码查询 */
    VipPricing selectByCode(@Param("productCode") String productCode);

    /** 更新价格 */
    int updatePrice(@Param("id") Long id, @Param("priceYuan") BigDecimal priceYuan);
}
