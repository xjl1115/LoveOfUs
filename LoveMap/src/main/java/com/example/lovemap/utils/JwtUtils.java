package com.example.lovemap.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.Map;

@Slf4j
@Component
public class JwtUtils {

    @Value("${jwt.secret}")
    private String secretString;

    @Value("${jwt.ttl}")
    private long ttlMillis;

    @Value("${jwt.refresh-ttl}")
    private long refreshTtlMillis;

    private SecretKey secretKey;

    // Token类型常量
    public static final String TOKEN_TYPE_ACCESS = "access";
    public static final String TOKEN_TYPE_REFRESH = "refresh";
    public static final String CLAIM_TOKEN_TYPE = "tokenType";

    @PostConstruct
    public void init() {
        if (secretString == null || secretString.isEmpty()) {
            throw new IllegalStateException("JWT 密钥未配置，请检查 jwt.secret 配置");
        }
        if (secretString.length() >= 32) {
            this.secretKey = Keys.hmacShaKeyFor(secretString.getBytes());
        } else {
            throw new IllegalStateException("JWT 密钥长度必须至少 32 字符");
        }
    }

    /**
     * 生成访问令牌（短期有效）
     */
    public String createAccessToken(Map<String, Object> claims) {
        Map<String, Object> finalClaims = new java.util.HashMap<>(claims);
        finalClaims.put(CLAIM_TOKEN_TYPE, TOKEN_TYPE_ACCESS);
        return createJWT(finalClaims, ttlMillis);
    }

    /**
     * 生成刷新令牌（长期有效）
     */
    public String createRefreshToken(Map<String, Object> claims) {
        Map<String, Object> finalClaims = new java.util.HashMap<>(claims);
        finalClaims.put(CLAIM_TOKEN_TYPE, TOKEN_TYPE_REFRESH);
        return createJWT(finalClaims, refreshTtlMillis);
    }

    /**
     * 生成jwt
     */
    public String createJWT(Map<String, Object> claims) {
        return createJWT(claims, ttlMillis);
    }

    /**
     * 生成jwt，指定过期时间
     */
    public String createJWT(Map<String, Object> claims, long ttl) {
        long expMillis = System.currentTimeMillis() + ttl;
        Date exp = new Date(expMillis);

        return Jwts.builder()
                .claims(claims)
                .expiration(exp)
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Token 解密
     */
    public Claims parseJWT(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 解析 JWT Token（不验证过期时间）
     * 用于 token 刷新等场景
     */
    public Claims parseJWTWithoutExpiration(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            return e.getClaims();
        }
    }

    /**
     * 获取token类型
     */
    public String getTokenType(String token) {
        try {
            Claims claims = parseJWT(token);
            return (String) claims.get(CLAIM_TOKEN_TYPE);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 验证是否为刷新令牌
     */
    public boolean isRefreshToken(String token) {
        return TOKEN_TYPE_REFRESH.equals(getTokenType(token));
    }

    /**
     * 验证是否为访问令牌
     */
    public boolean isAccessToken(String token) {
        String type = getTokenType(token);
        return type == null || TOKEN_TYPE_ACCESS.equals(type);
    }

    /**
     * 获取 token 剩余有效期（毫秒）
     * @param token JWT token
     * @return 剩余有效期，已过期返回负数，解析失败返回 null
     */
    public Long getRemainingTime(String token) {
        try {
            Claims claims = parseJWT(token);
            Date expiration = claims.getExpiration();
            return expiration.getTime() - System.currentTimeMillis();
        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            // Token 已过期
            return -1L;
        } catch (Exception e) {
            log.warn("获取 token 剩余有效期失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 检查 token 是否需要刷新（即将过期或已过期）
     * @param token JWT token
     * @param thresholdMillis 阈值（毫秒），低于此值则建议刷新
     * @return true 需要刷新，false 不需要
     */
    public boolean needsRefresh(String token, long thresholdMillis) {
        Long remainingTime = getRemainingTime(token);
        // 解析失败视为需要刷新（保守策略）
        if (remainingTime == null) {
            return true;
        }
        // 已过期或剩余时间低于阈值，需要刷新
        return remainingTime <= thresholdMillis;
    }

    /**
     * 检查 token 是否需要刷新（默认阈值 5 分钟）
     */
    public boolean needsRefresh(String token) {
        // 默认阈值 5 分钟 = 5 * 60 * 1000 = 300000 毫秒
        return needsRefresh(token, 5 * 60 * 1000L);
    }

    /**
     * 刷新 JWT Token（延长过期时间 - 滑动会话）
     * 仅在 token 过期或即将过期时调用
     * @param token 原始 token
     * @return 新的 token
     */
    public String refreshToken(String token) {
        try {
            // 使用不验证过期时间的方法解析 token，支持过期 token 的刷新
            Claims claims = parseJWTWithoutExpiration(token);
            // 创建新的claims，移除过期时间相关字段，使createJWT重新计算过期时间
            Map<String, Object> newClaims = new java.util.HashMap<>(claims);
            newClaims.remove(CLAIM_TOKEN_TYPE);  // 移除可能存在的token类型
            // 重新设置token类型为access
            newClaims.put(CLAIM_TOKEN_TYPE, TOKEN_TYPE_ACCESS);
            // 使用新的claims生成token，过期时间会重新计算为当前时间 + ttlMillis
            return createJWT(newClaims, ttlMillis);
        } catch (Exception e) {
            log.warn("刷新 token 失败：{}", e.getMessage());
            return null;
        }
    }

    /**
     * 获取过期时间
     * @return
     */
    public long getExpiration() {
        return ttlMillis;
    }
}