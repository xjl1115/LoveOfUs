package com.example.lovemap.model.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 化妆建议次卡入账记录
 * <p>
 * 顾问开通 MAKEOVER 次卡订单时写入；quota() 按月累加到化妆建议月度额度。
 * 不与 quota_log 共用：化妆建议走 makeover_record 统计已用，
 * 本表只承担"按月入账 + 过期归零"职责，避免污染导出配额语义。
 *
 * <p>对应数据库表：makeover_quota_grant
 */
@Data
public class MakeoverQuotaGrant {

    /**
     * 主键
     */
    private Long id;

    /**
     * 用户 ID
     */
    private Long userId;

    /**
     * 来源订单号，唯一（顾问重复开通会触发唯一键冲突兜底）
     */
    private String orderNo;

    /**
     * 本次入账次数
     */
    private Integer grantCount;

    /**
     * 归属月份 YYYY-MM；quota() 只累加当月入账
     */
    private String grantPeriod;

    /**
     * 开通时间
     */
    private LocalDateTime activatedAt;

    /**
     * 过期时间：本月底 23:59:59
     */
    private LocalDateTime expiresAt;

    private LocalDateTime createdAt;
}
