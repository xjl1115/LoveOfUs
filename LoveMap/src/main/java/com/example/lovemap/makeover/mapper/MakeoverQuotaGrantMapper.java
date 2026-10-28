package com.example.lovemap.makeover.mapper;

import com.example.lovemap.model.entity.MakeoverQuotaGrant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 化妆次卡入账 Mapper
 */
@Mapper
public interface MakeoverQuotaGrantMapper {

    /**
     * 写入一条入账记录（开通次卡时调用，orderNo 唯一键防止重复开通）
     */
    int insert(MakeoverQuotaGrant grant);

    /**
     * 按订单号查询（顾问重复开通幂等校验）
     */
    MakeoverQuotaGrant selectByOrderNo(@Param("orderNo") String orderNo);

    /**
     * 统计某用户累计入账总次数（次卡不过期，不按月份过滤）
     *
     * @return 入账总次数，无记录时返回 0
     */
    Integer sumAll(@Param("userId") Long userId);
}
