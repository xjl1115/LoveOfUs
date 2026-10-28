package com.example.lovemap.common.constant;

/**
 * VIP 次卡产品 → 配额服务 quotaType 映射
 */
public final class QuotaProductMapping {

    private QuotaProductMapping() {}

    /**
     * 把次卡产品类型映射到 {@link com.example.lovemap.common.constant.QuotaType}。
     * AI 化妆建议走独立入账表（makeover_quota_grant），不写入 quota_log；
     * 因此在 toQuotaType 里返回 -1 表示"不经过 quota_log"。
     *
     * @return quotaType 或 -1（不经过 quota_log 的次卡类型）
     */
    public static int toQuotaType(int productType) {
        return switch (productType) {
            case VipConstant.QUOTA_PRODUCT_DATE_PLAN -> QuotaType.AI_RECOMMEND;
            case VipConstant.QUOTA_PRODUCT_WISHLIST -> QuotaType.AI_WISHLIST;
            default -> -1;
        };
    }

    /**
     * 反向映射：{@link QuotaType} → 次卡产品类型，用于统计已开通次卡带来的入账次数。
     *
     * @return 次卡产品类型；导出与备份无对应次卡，返回 -1
     */
    public static int toProductType(int quotaType) {
        return switch (quotaType) {
            case QuotaType.AI_RECOMMEND -> VipConstant.QUOTA_PRODUCT_DATE_PLAN;
            case QuotaType.AI_WISHLIST -> VipConstant.QUOTA_PRODUCT_WISHLIST;
            default -> -1;
        };
    }

    /**
     * 是否为走独立入账表的次卡类型（目前仅 AI 化妆建议）。
     * <p>
     * 调用方在写 quota_log 之前应先走此分支，避免被 toQuotaType 返回 -1
     * 的兜底分支误判为"暂不支持自动开通"。
     */
    public static boolean isMakeoverProduct(int productType) {
        return productType == VipConstant.QUOTA_PRODUCT_MAKEOVER;
    }
}
