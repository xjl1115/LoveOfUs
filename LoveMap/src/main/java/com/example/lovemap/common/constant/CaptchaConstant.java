package com.example.lovemap.common.constant;

import lombok.Data;

/**
 * 验证码模块常量
 */
@Data
public final class CaptchaConstant {

    /**
     * 验证码 Redis KEY 前缀
     */
    public static final String CAPTCHA_KEY_PREFIX = "captcha:code:";

    /**
     * 限流 Redis KEY 前缀
     */
    public static final String RATE_LIMIT_KEY_PREFIX = "captcha:rate:";

    /**
     * 限流滑动窗口大小（分钟）
     */
    public static final long RATE_LIMIT_WINDOW_MINUTES = 10;

    /**
     * 滑动窗口内最大发送次数
     */
    public static final long RATE_LIMIT_MAX_COUNT = 5;

    /**
     * 用户登录 Session Redis KEY 前缀
     * 存储已登录的用户ID，用于校验是否重复登录
     * 格式：user:login:session:{nickname}
     */
    public static final String USER_LOGIN_SESSION_PREFIX = "user:login:session:";

    /**
     * 绑定码 Redis KEY 前缀
     * 存储用户的伴侣绑定码，不设置过期时间
     * 格式：user:bind:code:{userId}
     */
    public static final String BIND_CODE_PREFIX = "user:bind:code:";

    /**
     * 登录失败次数 Redis KEY 前缀
     * 格式：login:fail:{account}
     */
    public static final String LOGIN_FAIL_PREFIX = "login:fail:";

    /**
     * 登录锁定 Redis KEY 前缀
     * 格式：login:lock:{account}
     */
    public static final String LOGIN_LOCK_PREFIX = "login:lock:";

    /**
     * 最大登录失败次数
     */
    public static final int MAX_LOGIN_ATTEMPTS = 5;

    /**
     * 登录锁定时间（分钟）
     */
    public static final int LOCK_DURATION_MINUTES = 1;
}
