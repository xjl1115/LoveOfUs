package com.example.lovemap.model.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * VIP 订单
 */
@Data
public class VipOrder {

    /**
     * 主键
     */
    private Long id;

    /**
     * 订单号
     */
    private String orderNo;

    /**
     * 下单用户 ID
     */
    private Long userId;

    /**
     * 下单时的情侣组 ID
     */
    private Long groupId;

    /**
     * VIP 档位：1-周卡，2-月卡，3-季卡，4-年卡，5-永久
     */
    private Integer vipLevel;

    /**
     * 订单类型：1-VIP 卡，2-次卡
     */
    private Integer orderType;

    /**
     * 次卡配额类型：1=AI化妆 2=AI约会 3=AI心愿
     */
    private Integer quotaType;

    /**
     * 次卡次数（orderType=2 时使用）
     */
    private Integer quotaCount;

    /**
     * 下单时价格（元）
     */
    private BigDecimal priceYuan;

    /**
     * 订单状态：0-待开通，1-已开通，2-已取消
     */
    private Integer status;

    /**
     * 下单时间
     */
    private LocalDateTime createdAt;

    /**
     * 顾问开通时间，VIP 到期时间以此为基准
     */
    private LocalDateTime activatedAt;
}
