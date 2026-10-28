package com.example.lovemap.service;

import com.example.lovemap.common.Result;
import com.example.lovemap.model.dto.PointProductSaveDTO;
import com.example.lovemap.model.dto.PointExchangeDTO;
import com.example.lovemap.model.entity.PointExchange;
import com.example.lovemap.model.entity.PointProduct;
import com.example.lovemap.model.vo.PointOrderVO;
import com.example.lovemap.model.vo.PointProductVO;

import java.util.List;

/**
 * 积分商品 Service
 */
public interface PointProductService {

    /** 前端用户：列已上架商品（带 Redis 缓存） */
    Result<List<PointProductVO>> listOnShelf();

    /** 顾问控制台：列出全部商品（含下架） */
    Result<List<PointProductVO>> listAll();

    /** 顾问控制台：保存（新建或更新） */
    Result<Long> save(PointProductSaveDTO dto);

    /** 顾问控制台：删除 */
    Result<Void> delete(Long id);

    /** 用户兑换 / 下单（纯积分即时发放；含金额时生成待确认订单） */
    Result<PointOrderVO> exchange(Integer userId, PointExchangeDTO dto);

    /** 当前用户最近兑换 / 下单记录 */
    Result<List<PointExchange>> recentExchanges(Integer userId);

    /** 顾问控制台：待确认收款的商品订单 */
    Result<List<PointExchange>> listPendingOrders();

    /** 顾问控制台：确认收款并发放（扣积分 → 扣库存 → 置为已发放） */
    Result<PointExchange> activateOrder(String orderNo);

    /** 顾问控制台：取消待确认的商品订单 */
    Result<Void> cancelOrder(String orderNo);

    /** 内部：将 entity 转为 VO */
    PointProductVO toVO(PointProduct p);
}
