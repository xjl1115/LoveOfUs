package com.example.lovemap.common.constant;

import java.util.List;

/**
 * VIP 会员模块常量
 * <p>
 * 档位定义（等级 / 名称 / 时长 / 权益）集中在此维护，价格统一落库到 vip_pricing 表，
 * 通过 {@code VipPricingService} 读取。调整价格无需改动代码或重启服务。
 */
public final class VipConstant {

    private VipConstant() {
    }

    /** VIP 等级：普通用户（未开通） */
    public static final int LEVEL_NORMAL = 0;

    /** VIP 等级：周卡 */
    public static final int LEVEL_WEEK = 1;

    /** VIP 等级：月卡 */
    public static final int LEVEL_MONTH = 2;

    /** VIP 等级：季卡 */
    public static final int LEVEL_QUARTER = 3;

    /** VIP 等级：年卡 */
    public static final int LEVEL_YEAR = 4;

    /** VIP 等级：永久 */
    public static final int LEVEL_FOREVER = 5;

    /** 普通用户名称 */
    public static final String LEVEL_NORMAL_NAME = "普通";

    /** 顾问开通接口的密钥请求头，取值来自配置 vip.admin-token */
    public static final String ADMIN_TOKEN_HEADER = "X-Vip-Admin-Token";

    /** vip_pricing 表的业务编码：VIP 卡 */
    public static final String PRODUCT_CODE_VIP_WEEK = "vip_week";
    public static final String PRODUCT_CODE_VIP_MONTH = "vip_month";
    public static final String PRODUCT_CODE_VIP_QUARTER = "vip_quarter";
    public static final String PRODUCT_CODE_VIP_YEAR = "vip_year";
    public static final String PRODUCT_CODE_VIP_FOREVER = "vip_forever";

    /** vip_pricing 表的业务编码：次卡 */
    public static final String PRODUCT_CODE_QUOTA_MAKEOVER = "quota_makeover";
    public static final String PRODUCT_CODE_QUOTA_DATE_PLAN = "quota_date_plan";
    public static final String PRODUCT_CODE_QUOTA_WISHLIST = "quota_wishlist";

    /** 订单状态：待开通（已下单，等顾问收款后开通） */
    public static final int ORDER_STATUS_PENDING = 0;

    /** 订单状态：已开通 */
    public static final int ORDER_STATUS_ACTIVATED = 1;

    /** 订单状态：已取消（用户重新下单时，旧的待开通订单自动取消） */
    public static final int ORDER_STATUS_CANCELED = 2;

    /** 订单类型：VIP 卡 */
    public static final int ORDER_TYPE_VIP = 1;

    /** 订单类型：次卡 */
    public static final int ORDER_TYPE_QUOTA = 2;

    /** 次卡配额类型：AI 化妆建议 */
    public static final int QUOTA_PRODUCT_MAKEOVER = 1;

    /** 次卡配额类型：AI 约会建议 */
    public static final int QUOTA_PRODUCT_DATE_PLAN = 2;

    /** 次卡配额类型：AI 心愿建议 */
    public static final int QUOTA_PRODUCT_WISHLIST = 3;

    /**
     * 档位定义
     *
     * @param level         等级，与 user.vip_level 对应
     * @param name          档位名称
     * @param durationDays  时长（天），null 表示永久
     * @param benefits      该档位权益文案
     * @param makeoverBonus AI 化妆建议每月额外次数，null 表示不限量
     */
    public record Tier(int level, String name, Integer durationDays, List<String> benefits,
                       Integer makeoverBonus) {

        /** 是否永久卡 */
        public boolean permanent() {
            return durationDays == null;
        }

        /** 通过 vipLevel 取 productCode */
        public String productCode() {
            return switch (level) {
                case LEVEL_WEEK -> PRODUCT_CODE_VIP_WEEK;
                case LEVEL_MONTH -> PRODUCT_CODE_VIP_MONTH;
                case LEVEL_QUARTER -> PRODUCT_CODE_VIP_QUARTER;
                case LEVEL_YEAR -> PRODUCT_CODE_VIP_YEAR;
                case LEVEL_FOREVER -> PRODUCT_CODE_VIP_FOREVER;
                default -> null;
            };
        }
    }

    /**
     * 全部档位（按等级升序）
     * <p>
     * 价格统一从 vip_pricing 读取；这里只保留名称、时长、权益等静态定义。
     */
    public static final List<Tier> TIERS = List.of(
            new Tier(LEVEL_WEEK, "周卡", 7, List.of(
                    "情侣双人共享，伴侣同享全部权益",
                    "专属 VIP 身份标识",
                    "AI 化妆建议高清出图",
                    "AI 约会策划不限次"), 2),
            new Tier(LEVEL_MONTH, "月卡", 30, List.of(
                    "包含周卡全部权益",
                    "AI 情侣对话不限次",
                    "高清原图导出，无水印",
                    "历史记录永久保存"), 6),
            new Tier(LEVEL_QUARTER, "季卡", 90, List.of(
                    "包含月卡全部权益",
                    "高级海报模板库",
                    "AI 高峰时段优先队列",
                    "云端相册扩容至 20GB"), 9),
            new Tier(LEVEL_YEAR, "年卡", 365, List.of(
                    "包含季卡全部权益",
                    "年度恋爱报告",
                    "云端相册扩容至 100GB",
                    "专属顾问优先响应"), 13),
            new Tier(LEVEL_FOREVER, "永久卡", null, List.of(
                    "包含年卡全部权益",
                    "后续新增 VIP 功能全部可用",
                    "永久有效，无需续费",
                    "创始会员专属徽章"), null));

    /**
     * 计算某 VIP 等级每月的 AI 化妆建议可用次数（免费次数 + 档位额外次数）
     *
     * @param level     VIP 等级
     * @param freeQuota 每个账户每月免费次数（配置 makeover.monthly-free-quota）
     * @return 每月可用次数，null 表示不限量（永久卡）
     */
    public static Integer makeoverMonthlyQuota(Integer level, int freeQuota) {
        Tier tier = findTier(level);
        if (tier == null) {
            return freeQuota;
        }
        return tier.makeoverBonus() == null ? null : freeQuota + tier.makeoverBonus();
    }

    /**
     * VIP 会员页展示用的 AI 化妆建议额度文案
     */
    public static String makeoverQuotaText(Integer level, int freeQuota) {
        Integer quota = makeoverMonthlyQuota(level, freeQuota);
        return quota == null ? "AI 化妆建议不限次" : "AI 化妆建议 " + quota + " 次/月";
    }

    /**
     * 按等级查档位
     *
     * @param level 等级
     * @return 档位，等级非法时返回 null
     */
    public static Tier findTier(Integer level) {
        if (level == null) {
            return null;
        }
        for (Tier tier : TIERS) {
            if (tier.level() == level) {
                return tier;
            }
        }
        return null;
    }

    /**
     * 等级对应名称，未知等级按普通用户处理
     */
    public static String levelName(Integer level) {
        Tier tier = findTier(level);
        return tier != null ? tier.name() : LEVEL_NORMAL_NAME;
    }

    /**
     * 次卡配额类型对应名称
     */
    public static String quotaProductName(Integer quotaType) {
        if (quotaType == null) {
            return "";
        }
        return switch (quotaType) {
            case QUOTA_PRODUCT_MAKEOVER -> "AI 化妆建议";
            case QUOTA_PRODUCT_DATE_PLAN -> "AI 约会建议";
            case QUOTA_PRODUCT_WISHLIST -> "AI 心愿建议";
            default -> "未知";
        };
    }

    /**
     * 次卡配额类型对应业务编码
     */
    public static String quotaProductCode(Integer quotaType) {
        if (quotaType == null) {
            return null;
        }
        return switch (quotaType) {
            case QUOTA_PRODUCT_MAKEOVER -> PRODUCT_CODE_QUOTA_MAKEOVER;
            case QUOTA_PRODUCT_DATE_PLAN -> PRODUCT_CODE_QUOTA_DATE_PLAN;
            case QUOTA_PRODUCT_WISHLIST -> PRODUCT_CODE_QUOTA_WISHLIST;
            default -> null;
        };
    }
}
