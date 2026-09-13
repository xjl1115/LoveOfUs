package com.example.lovemap.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * VIP 待开通订单 VO（顾问侧）
 * <p>
 * 顾问据订单号定位订单并收款，开通后该订单不再出现在列表中。
 */
@Data
public class VipPendingOrderVO {

    /**
     * 订单号
     */
    private String orderNo;

    /**
     * 下单用户 ID
     */
    private Long userId;

    /**
     * 下单用户昵称
     */
    private String nickname;

    /**
     * VIP 档位：1-周卡，2-月卡，3-季卡，4-年卡，5-永久
     */
    private Integer vipLevel;

    /**
     * 档位名称
     */
    private String vipLevelName;

    /**
     * 价格（元）
     */
    private Integer priceYuan;

    /**
     * 下单时间，也是 VIP 到期时间的计算基准
     */
    private LocalDateTime createdAt;
}