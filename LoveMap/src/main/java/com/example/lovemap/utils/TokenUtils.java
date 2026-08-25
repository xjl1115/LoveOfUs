package com.example.lovemap.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import com.example.lovemap.common.constant.UserConstant;

/**
 * Token 工具类
 * 提供从 Token 中提取用户信息的便捷方法
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TokenUtils {

    private final JwtUtils jwtUtils;
    private final StringRedisTemplate stringRedisTemplate;

    // Token 中存储的 Claims 键名常量
    public static final String CLAIM_USER_ID = "userId";
    public static final String CLAIM_ROLE = "nickName";

    // Bearer 前缀
    public static final String BEARER_PREFIX = "Bearer ";

    /**
     * 从 Authorization 头中提取纯 Token
     * 支持 "Bearer xxx" 格式或直接传入 token
     *
     * @param authorization Authorization 头值或纯 token
     * @return 纯 JWT token
     */
    public String extractToken(String authorization) {
        if (authorization != null && authorization.startsWith(BEARER_PREFIX)) {
            return authorization.substring(BEARER_PREFIX.length());
        }
        return authorization;
    }

    /**
     * 从 Token 中获取用户ID
     * 支持传入 "Bearer xxx" 格式或直接传入 token
     *
     * @param token JWT Token 或 Authorization 头
     * @return 用户ID，解析失败返回 null
     */
    public Integer getUserId(String token) {
        try {
            String pureToken = extractToken(token);
            Claims claims = parseToken(pureToken);
            if (claims == null) {
                return null;
            }
            Object userId = claims.get(CLAIM_USER_ID);
            if (userId instanceof Number) {
                return ((Number) userId).intValue();
            }
            return null;
        } catch (Exception e) {
            log.warn("从 Token 获取用户ID失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 验证 Token 是否有效
     * 支持传入 "Bearer xxx" 格式或直接传入 token
     *
     * @param token JWT Token 或 Authorization 头
     * @return true-有效，false-无效
     */
    public boolean validateToken(String token) {
        try {
            String pureToken = extractToken(token);
            // 先检查token是否在黑名单中
            if (isTokenBlacklisted(pureToken)) {
                log.debug("Token 已被加入黑名单");
                return false;
            }
            jwtUtils.parseJWT(pureToken);
            return true;
        } catch (ExpiredJwtException e) {
            log.debug("Token 已过期");
            return false;
        } catch (JwtException e) {
            log.debug("Token 验证失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 检查 Token 是否在黑名单中
     * 支持传入 "Bearer xxx" 格式或直接传入 token
     *
     * @param token JWT Token 或 Authorization 头
     * @return true-在黑名单中，false-不在黑名单中
     */
    public boolean isTokenBlacklisted(String token) {
        String pureToken = extractToken(token);
        if (!isValidTokenFormat(pureToken)) {
            return true;
        }
        String blacklistKey = UserConstant.TOKEN_BLACKLIST + pureToken;
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(blacklistKey));
    }

    /**
     * 检查 Token 是否即将过期
     * 支持传入 "Bearer xxx" 格式或直接传入 token
     *
     * @param token JWT Token 或 Authorization 头
     * @param thresholdMillis 阈值（毫秒）
     * @return true-即将过期，false-未过期或无效
     */
    public boolean isTokenExpiringSoon(String token, long thresholdMillis) {
        String pureToken = extractToken(token);
        return jwtUtils.needsRefresh(pureToken, thresholdMillis);
    }

    /**
     * 检查 Token 是否即将过期（默认5分钟阈值）
     * 支持传入 "Bearer xxx" 格式或直接传入 token
     *
     * @param token JWT Token 或 Authorization 头
     * @return true-即将过期，false-未过期或无效
     */
    public boolean isTokenExpiringSoon(String token) {
        String pureToken = extractToken(token);
        return jwtUtils.needsRefresh(pureToken);
    }

    /**
     * 刷新 Token
     * 支持传入 "Bearer xxx" 格式或直接传入 token
     *
     * @param token 原 Token 或 Authorization 头
     * @return 新 Token，刷新失败返回 null
     */
    public String refreshToken(String token) {
        String pureToken = extractToken(token);
        return jwtUtils.refreshToken(pureToken);
    }

    /**
     * 获取 Token 剩余有效时间
     * 支持传入 "Bearer xxx" 格式或直接传入 token
     *
     * @param token JWT Token 或 Authorization 头
     * @return 剩余毫秒数，已过期返回负数，解析失败返回 null
     */
    public Long getRemainingTime(String token) {
        String pureToken = extractToken(token);
        return jwtUtils.getRemainingTime(pureToken);
    }

    /**
     * 解析 Token 获取 Claims
     *
     * @param token JWT Token
     * @return Claims 对象，解析失败返回 null
     */
    private Claims parseToken(String token) {
        if (!isValidTokenFormat(token)) {
            return null;
        }
        try {
            return jwtUtils.parseJWT(token);
        } catch (ExpiredJwtException e) {
            log.debug("Token 已过期");
            return null;
        } catch (JwtException e) {
            log.warn("Token 解析失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 验证 Token 格式是否有效
     *
     * @param token JWT Token
     * @return true-格式有效，false-格式无效
     */
    private boolean isValidTokenFormat(String token) {
        return token != null && !token.isEmpty() && token.split("\\.").length == 3;
    }

    /**
     * 创建访问令牌
     *
     * @param userId   用户ID
     * @param username 用户名
     * @param role     角色
     * @return JWT Token
     */
    public String createAccessToken(Integer userId, String username, String role) {
        Map<String, Object> claims = Map.of(
                CLAIM_USER_ID, userId,
                CLAIM_ROLE, role
        );
        return jwtUtils.createAccessToken(claims);
    }

    /**
     * 创建刷新令牌
     *
     * @param userId   用户ID
     * @param username 用户名
     * @return JWT Token
     */
    public String createRefreshToken(Integer userId, String username) {
        Map<String, Object> claims = Map.of(
                CLAIM_USER_ID, userId
        );
        return jwtUtils.createRefreshToken(claims);
    }
}
