package com.example.lovemap.service;

import com.example.lovemap.common.constant.CaptchaConstant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * Redis 滑动窗口限流服务
 * 用于邮箱验证码的发送频率控制
 */
@Slf4j
@Service
public class RateLimiterService {

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> redisScript;

    /**
     * Lua 脚本：滑动窗口限流
     * KEYS[1] = key
     * ARGV[1] = 窗口开始时间戳
     * ARGV[2] = 当前时间戳
     * ARGV[3] = 最大次数
     * ARGV[4] = key过期时间（秒）
     */
    private static final String LUA_SCRIPT =
            "redis.call('ZREMRANGEBYSCORE', KEYS[1], 0, ARGV[1]) " +
            "local count = redis.call('ZCARD', KEYS[1]) " +
            "if count < tonumber(ARGV[3]) then " +
            "    redis.call('ZADD', KEYS[1], ARGV[2], ARGV[2]) " +
            "    redis.call('EXPIRE', KEYS[1], ARGV[4]) " +
            "    return 1 " +
            "else " +
            "    return 0 " +
            "end";

    public RateLimiterService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.redisScript = new DefaultRedisScript<>();
        this.redisScript.setScriptText(LUA_SCRIPT);
        this.redisScript.setResultType(Long.class);
    }

    /**
     * 检查并记录发送次数（滑动窗口）
     *
     * @param email 邮箱地址
     * @return true=允许发送，false=超过限流
     */
    public boolean checkAndRecord(String email) {
        String key = buildKey(email);
        long now = System.currentTimeMillis();
        long windowStart = now - TimeUnit.MINUTES.toMillis(CaptchaConstant.RATE_LIMIT_WINDOW_MINUTES);
        long expireSeconds = TimeUnit.MINUTES.toSeconds(CaptchaConstant.RATE_LIMIT_WINDOW_MINUTES);

        Long result = redisTemplate.execute(
                redisScript,
                Collections.singletonList(key),
                String.valueOf(windowStart),
                String.valueOf(now),
                String.valueOf(CaptchaConstant.RATE_LIMIT_MAX_COUNT),
                String.valueOf(expireSeconds)
        );

        boolean allowed = result != null && result == 1L;
        if (!allowed) {
            log.warn("邮箱 {} 发送过于频繁，已限流", email);
        }
        return allowed;
    }

    /**
     * 获取当前窗口内已发送次数
     */
    public long getCurrentCount(String email) {
        String key = buildKey(email);
        long windowStart = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(CaptchaConstant.RATE_LIMIT_WINDOW_MINUTES);
        redisTemplate.opsForZSet().removeRangeByScore(key, 0, windowStart);
        Long count = redisTemplate.opsForZSet().zCard(key);
        return count != null ? count : 0;
    }

    /**
     * 获取剩余可发送次数
     */
    public long getRemainingCount(String email) {
        return CaptchaConstant.RATE_LIMIT_MAX_COUNT - getCurrentCount(email);
    }

    /**
     * 获取限流窗口大小（秒）
     */
    public long getWindowSeconds() {
        return TimeUnit.MINUTES.toSeconds(CaptchaConstant.RATE_LIMIT_WINDOW_MINUTES);
    }

    /**
     * 构建 Redis KEY
     */
    private String buildKey(String email) {
        return CaptchaConstant.RATE_LIMIT_KEY_PREFIX + email;
    }
}
