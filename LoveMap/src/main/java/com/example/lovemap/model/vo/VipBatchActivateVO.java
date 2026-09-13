package com.example.lovemap.model.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * VIP 批量开通结果 VO
 * <p>
 * 逐单开通、逐单反馈：单笔失败不影响其他订单，顾问按下单结果逐条核对。
 */
@Data
public class VipBatchActivateVO {

    /**
     * 请求开通的订单总数
     */
    private int total;

    /**
     * 开通成功数
     */
    private int successCount;

    /**
     * 开通失败数
     */
    private int failCount;

    /**
     * 逐单开通结果，顺序与请求的订单号一致
     */
    private List<Item> results;

    /**
     * 单笔开通结果
     */
    @Data
    public static class Item {

        /**
         * 订单号
         */
        private String orderNo;

        /**
         * 是否开通成功
         */
        private boolean success;

        /**
         * 结果说明，失败时为失败原因
         */
        private String message;

        /**
         * 开通后的 VIP 档位，失败时为 null
         */
        private Integer vipLevel;

        /**
         * 开通后的到期时间，失败或永久卡为 null
         */
        private LocalDateTime vipExpireAt;
    }
}