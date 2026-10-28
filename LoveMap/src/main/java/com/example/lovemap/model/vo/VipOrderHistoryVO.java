package com.example.lovemap.model.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 历史订单 VO（顾问侧）
 * <p>
 * 包含全状态订单（待开通 / 已开通 / 已取消），含下单用户手机号与邮箱便于回溯。
 */
@Data
public class VipOrderHistoryVO {

    /** 订单号 */
    private String orderNo;

    /** 下单用户 ID */
    private Long userId;

    /** 下单用户昵称 */
    private String nickname;

    /** 用户手机号（明文） */
    private String phone;

    /** 用户邮箱（明文） */
    private String email;

    /** VIP 档位等级 */
    private Integer vipLevel;

    /** 档位名称 */
    private String vipLevelName;

    /** 订单类型：1=VIP 卡，2=次卡 */
    private Integer orderType;

    /** 次卡配额类型：1=AI化妆 2=AI约会 3=AI心愿 */
    private Integer quotaType;

    /** 次卡配额类型名称 */
    private String quotaTypeName;

    /** 次卡次数 */
    private Integer quotaCount;

    /** 价格（元） */
    private BigDecimal priceYuan;

    /** 订单状态：0-待开通 1-已开通 2-已取消 */
    private Integer status;

    /** 订单状态名称 */
    private String statusName;

    /** 下单时间 */
    private LocalDateTime createdAt;

    /** 开通时间 */
    private LocalDateTime activatedAt;
}
