package com.example.lovemap.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 服务层公共辅助方法
 * 提取各 ServiceImpl 中重复的缓存操作和工具方法
 */
@Slf4j
public final class ServiceHelper {

    private ServiceHelper() {}

    // ==================== 缓存操作 ====================

    /**
     * 从 Redis 缓存获取数据（普通类型）
     *
     * @param redisTemplate Redis 模板
     * @param objectMapper  JSON 序列化器
     * @param cacheKey      缓存键
     * @param clazz         目标类型
     * @return 缓存数据，不存在或解析失败返回 null
     */
    public static <T> T getFromCache(StringRedisTemplate redisTemplate, ObjectMapper objectMapper,
                                     String cacheKey, Class<T> clazz) {
        String cachedJson = redisTemplate.opsForValue().get(cacheKey);
        if (cachedJson != null) {
            try {
                return objectMapper.readValue(cachedJson, clazz);
            } catch (JsonProcessingException e) {
                log.warn("反序列化缓存失败, key: {}", cacheKey, e);
            }
        }
        return null;
    }

    /**
     * 从 Redis 缓存获取数据（泛型类型，如 List）
     *
     * @param redisTemplate Redis 模板
     * @param objectMapper  JSON 序列化器
     * @param cacheKey      缓存键
     * @param typeRef       类型引用
     * @return 缓存数据，不存在或解析失败返回 null
     */
    public static <T> T getFromCache(StringRedisTemplate redisTemplate, ObjectMapper objectMapper,
                                     String cacheKey, TypeReference<T> typeRef) {
        String cachedJson = redisTemplate.opsForValue().get(cacheKey);
        if (cachedJson != null) {
            try {
                return objectMapper.readValue(cachedJson, typeRef);
            } catch (JsonProcessingException e) {
                log.warn("反序列化缓存失败, key: {}", cacheKey, e);
            }
        }
        return null;
    }

    /**
     * 将数据存入 Redis 缓存
     *
     * @param redisTemplate Redis 模板
     * @param objectMapper  JSON 序列化器
     * @param cacheKey      缓存键
     * @param data          要缓存的数据
     */
    public static void putToCache(StringRedisTemplate redisTemplate, ObjectMapper objectMapper,
                                  String cacheKey, Object data) {
        try {
            String json = objectMapper.writeValueAsString(data);
            redisTemplate.opsForValue().set(cacheKey, json, CACHE_TTL);
        } catch (JsonProcessingException e) {
            log.warn("序列化缓存失败, key: {}", cacheKey, e);
        }
    }

    /**
     * 缓存默认 TTL：作为 unlink 失败时的兜底，缓存最多保留 CACHE_TTL 时间
     */
    private static final java.time.Duration CACHE_TTL = java.time.Duration.ofMinutes(10);

    // ==================== 缓存 Key 构建 ====================

    /**
     * 构建缓存 key：有 groupId 用 groupId，否则用 userId
     *
     * @param prefix  前缀
     * @param groupId 群组ID（可为 null）
     * @param userId  用户ID
     * @return 缓存 key
     */
    public static String buildCacheKey(String prefix, Long groupId, Long userId) {
        return prefix + (groupId != null ? groupId : userId);
    }

    // ==================== OSS 工具 ====================

    /**
     * 从 OSS 访问 URL 中提取对象键
     * URL 格式: https://{bucket}.{endpoint}/{objectKey}
     *
     * @param ossUrl OSS 访问 URL
     * @return 对象键，解析失败返回 null
     */
    public static String extractObjectKey(String ossUrl) {
        if (ossUrl == null || ossUrl.isEmpty()) {
            return null;
        }
        try {
            int slashIndex = ossUrl.indexOf('/', 8); // 跳过 https://
            if (slashIndex > 0) {
                int pathStart = ossUrl.indexOf('/', slashIndex + 1);
                if (pathStart > 0) {
                    return ossUrl.substring(pathStart + 1);
                }
            }
        } catch (Exception e) {
            log.warn("解析 OSS URL 失败: {}", ossUrl, e);
        }
        return null;
    }
}
