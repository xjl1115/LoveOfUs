package com.example.lovemap.common.constant;

/**
 * 用户模块常量
 */
public final class UserConstant {


    private UserConstant() {
    }

    /**
     * Token 黑名单 Redis KEY 前缀
     * 存储已失效的Token，用于校验Token是否被主动登出
     * 格式：token:blacklist:{token}
     */
    public static final String TOKEN_BLACKLIST = "token:blacklist:";

    public static final String USER_INFO = "user:info:";  // + userId

    /**
     * 用户统计数据 Redis KEY 前缀
     * 格式：user:stats:{userId}
     * 过期时间：每天零点自动过期
     */
    public static final String USER_STATS = "user:stats:";

    /**
     * 用户通知设置 Redis KEY 前缀
     * 格式：user:notification:settings:{userId}
     */
    public static final String USER_NOTIFICATION_SETTINGS = "user:notification:settings:";

    /**
     * 默认头像URL（新用户注册时使用）
     */
    public static final String DEFAULT_AVATAR_URL =
            "https://allenxjl.oss-cn-beijing.aliyuncs.com/avatar/3a7b1ac1b22a345ad56bc8a302cf3af775aa8372f4b7-1xJO27_fw1200webp.webp";
}
