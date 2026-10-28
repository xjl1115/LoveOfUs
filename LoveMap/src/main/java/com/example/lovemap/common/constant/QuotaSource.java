package com.example.lovemap.common.constant;

/**
 * 配额消耗来源（quota_log.quota_source / makeover_record.quota_source）
 */
public final class QuotaSource {

    private QuotaSource() {}

    /** 周期基础额度：AI 每周、导出与化妆建议每月，随周期重置 */
    public static final int PERIOD = 1;

    /** 次卡入账：不过期，用完为止 */
    public static final int PURCHASED = 2;
}
