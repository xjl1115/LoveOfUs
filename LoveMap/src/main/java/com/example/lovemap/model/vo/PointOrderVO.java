package com.example.lovemap.model.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 积分商品下单结果 VO
 * <p>
 * 与 VipOrderVO 同构：下单成功后返回订单号与专属顾问邮箱，
 * 用户据此联系顾问完成线下付款，顾问在后台确认后再扣积分发放。
 */
@Data
public class PointOrderVO {

    /** 订单号，用户联系顾问时提供；纯积分兑换为 null */
    private String orderNo;

    /** 商品名称 */
    private String productName;

    /** 抵扣积分合计 */
    private Integer totalPoints;

    /** 需付款金额（元），null 表示纯积分兑换 */
    private BigDecimal priceAmount;

    /** 0=待顾问确认收款 1=已发放 2=已取消 */
    private Integer status;

    /** 专属顾问邮箱 */
    private String advisorEmail;
}
