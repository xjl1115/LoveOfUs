package com.example.lovemap.model.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * VIP 订单 VO
 * <p>
 * 下单、顾问开通与账单列表共用：下单返回待开通订单，开通后再补上 vipExpireAt。
 */
@Data
public class VipOrderVO {

    /**
     * 订单号，用户联系顾问时提供
     */
    private String orderNo;

    /**
     * VIP 档位：1-周卡，2-月卡，3-季卡，4-年卡，5-永久
     */
    private Integer vipLevel;

    /**
     * 档位名称
     */
    private String vipLevelName;

    /**
     * 订单类型：1=VIP 卡，2=次卡
     */
    private Integer orderType;

    /**
     * 次卡配额类型：1=AI化妆 2=AI约会 3=AI心愿
     */
    private Integer quotaType;

    /**
     * 次卡配额类型名称
     */
    private String quotaTypeName;

    /**
     * 次卡次数
     */
    private Integer quotaCount;

    /**
     * 价格（元）
     */
    private BigDecimal priceYuan;

    /**
     * 订单状态：0-待开通，1-已开通，2-已取消
     */
    private Integer status;

    /**
     * 专属顾问邮箱，用户据此联系顾问完成支付
     */
    private String advisorEmail;

    /**
     * 开通后的到期时间，尚未开通或永久卡为 null
     */
    private LocalDateTime vipExpireAt;

    /**
     * 下单时间，账单列表展示
     */
    private LocalDateTime createdAt;

    /**
     * 顾问开通时间，未开通为 null
     */
    private LocalDateTime activatedAt;
}
