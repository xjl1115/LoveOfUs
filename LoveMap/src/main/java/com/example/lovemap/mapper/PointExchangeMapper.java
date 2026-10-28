package com.example.lovemap.mapper;

import com.example.lovemap.model.entity.PointExchange;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 积分商品订单 / 兑换记录 Mapper
 */
@Mapper
public interface PointExchangeMapper {

    /** 插入 */
    int insert(PointExchange exchange);

    /** 当前用户最近兑换 / 下单记录（最多 50 条） */
    List<PointExchange> selectRecentByUser(@Param("userId") Long userId);

    /** 按订单号查询 */
    PointExchange selectByOrderNo(@Param("orderNo") String orderNo);

    /** 待顾问确认收款的订单 */
    List<PointExchange> selectPending();

    /** 更新订单状态（确认发放 / 取消） */
    int updateStatus(@Param("id") Long id,
                     @Param("status") Integer status,
                     @Param("confirmedAt") java.time.LocalDateTime confirmedAt);
}
