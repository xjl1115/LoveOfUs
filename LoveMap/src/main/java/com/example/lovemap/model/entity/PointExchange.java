package com.example.lovemap.model.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 积分商品订单 / 兑换记录
 * 对应数据库表：point_exchange
 *
 * 设计要点：
 *   - product_name / points_cost 冗余保存，避免商品后续改名影响历史
 *   - priceAmount 为 NULL 表示纯积分兑换（即时发放）
 *   - priceAmount 不为 NULL 时走「下单 → 顾问线下收款 → 后台确认发放」流程
 *   - 订单状态：0=待顾问确认收款 1=已发放 2=已取消
 */
@Data
public class PointExchange {

    private Long id;
    /** 订单号，金额商品下单时生成；纯积分兑换为 NULL */
    private String orderNo;
    private Long userId;
    private Long productId;
    private String productName;
    private Integer pointsCost;
    private Integer quantity;
    private Integer totalPoints;
    /** 需付款金额（元），NULL=纯积分兑换 */
    private BigDecimal priceAmount;
    /** 收货人姓名（金额商品下单快照） */
    private String receiverName;
    /** 收货人手机号（金额商品下单快照） */
    private String receiverPhone;
    /** 收货地址（省+市+详细，金额商品下单快照） */
    private String shippingAddress;
    /** 0=待确认 1=已发放 2=已取消 */
    private Integer status;
    private LocalDateTime confirmedAt;
    private LocalDateTime createdAt;
}
