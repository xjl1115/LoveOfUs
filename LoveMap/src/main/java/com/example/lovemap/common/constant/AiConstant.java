package com.example.lovemap.common.constant;

/**
 * AI 模块常量
 * <p>
 * 集中管理：缓存 key、限流 key、限流阈值、采纳反馈默认值等。
 */
public final class AiConstant {

    private AiConstant() {
    }

    /**
     * 纪念日文案 Redis 缓存 KEY 前缀
     * 完整 key：ai:anniversary:copy:{anniversaryId}:{style}:{hash(extra)}
     */
    public static final String ANNIVERSARY_COPY_CACHE_PREFIX = "ai:anniversary:copy:";

    /**
     * 礼物推荐 Redis 缓存 KEY 前缀
     * 完整 key：ai:gift:{scene}:{hash(budget+gender+preference+extra)}
     */
    public static final String GIFT_RECOMMEND_CACHE_PREFIX = "ai:gift:";

    /**
     * 心愿清单礼物推荐 Redis 缓存 KEY 前缀
     * 完整 key：ai:gift:wishlist:{hash(wishlist+scene+budget)}
     */
    public static final String GIFT_WISHLIST_CACHE_PREFIX = "ai:gift:wishlist:";

    /**
     * AI 生成接口用户日限流 KEY 前缀
     * 完整 key：ai:rate:{userId}:{yyyymmdd}
     */
    public static final String USER_RATE_LIMIT_PREFIX = "ai:rate:";

    /**
     * 文案/礼物缓存有效期（秒）—— 1 小时
     */
    public static final long COPY_CACHE_TTL_SECONDS = 60 * 60L;

    /**
     * 礼物推荐缓存有效期（秒）—— 1 小时
     */
    public static final long GIFT_CACHE_TTL_SECONDS = 60 * 60L;

    /**
     * 单用户每天最多调用 AI 生成次数（次）
     */
    public static final long DAILY_MAX_INVOCATIONS = 30;

    /**
     * 采纳反馈：未采纳
     */
    public static final int FEEDBACK_NONE = 0;

    /**
     * 采纳反馈：采纳
     */
    public static final int FEEDBACK_ACCEPTED = 1;

    /**
     * 采纳反馈：拒绝
     */
    public static final int FEEDBACK_REJECTED = 2;
}