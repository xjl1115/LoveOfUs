package com.example.lovemap.service.support;

import com.example.lovemap.model.entity.CoupleTask;
import com.example.lovemap.model.vo.CoupleTaskVO;
import com.example.lovemap.utils.BusinessDateUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * 情侣任务缓存支持
 *
 * key 设计：
 *   couple:task:def          已启用任务定义（顾问改动后失效）
 *   couple:task:list:{userId}  某用户的任务列表（含周期状态；完成任务后失效）
 *
 * TTL 取「1 小时」与「距业务日零点」的较小值，
 * 保证跨零点后昨日状态自动失效，无需定时任务清理缓存。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CoupleTaskCacheSupport {

    /** 用户任务列表缓存前缀，完整 key 为 couple:task:list:{userId} */
    public static final String USER_LIST_KEY_PREFIX = "couple:task:list:";

    /** 任务定义缓存 key */
    public static final String TASK_DEF_KEY = "couple:task:def";

    private static final Duration MAX_TTL = Duration.ofHours(1);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final BusinessDateUtils businessDateUtils;

    /**
     * 读取某用户的缓存任务列表（无缓存返回 null）
     */
    public List<CoupleTaskVO> readUserList(Integer userId) {
        try {
            String json = redisTemplate.opsForValue().get(USER_LIST_KEY_PREFIX + userId);
            if (json == null) {
                return null;
            }
            return objectMapper.readValue(json, new TypeReference<List<CoupleTaskVO>>() {});
        } catch (Exception e) {
            log.warn("[情侣任务] 读取用户缓存失败，走 DB, userId={}", userId, e);
            return null;
        }
    }

    /**
     * 写入某用户的缓存任务列表
     */
    public void writeUserList(Integer userId, List<CoupleTaskVO> list) {
        if (list == null) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(USER_LIST_KEY_PREFIX + userId,
                    objectMapper.writeValueAsString(list), resolveTtl());
        } catch (Exception e) {
            log.warn("[情侣任务] 写入用户缓存失败, userId={}", userId, e);
        }
    }

    /**
     * 失效某用户的缓存（任务完成 / 领取积分后调用）
     */
    public void evictUserList(Integer userId) {
        try {
            redisTemplate.delete(USER_LIST_KEY_PREFIX + userId);
        } catch (Exception e) {
            log.warn("[情侣任务] 用户缓存失效失败, userId={}", userId, e);
        }
    }

    /**
     * 失效全部用户缓存（任务定义变更后调用）
     */
    public void evictAllUserLists() {
        try {
            var keys = redisTemplate.keys(USER_LIST_KEY_PREFIX + "*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (Exception e) {
            log.warn("[情侣任务] 用户缓存批量失效失败", e);
        }
    }

    /**
     * 读取已启用任务定义（无缓存返回 null）
     */
    public List<CoupleTask> readEnabledTasks() {
        try {
            String json = redisTemplate.opsForValue().get(TASK_DEF_KEY);
            if (json == null) {
                return null;
            }
            return objectMapper.readValue(json, new TypeReference<List<CoupleTask>>() {});
        } catch (Exception e) {
            log.warn("[情侣任务] 读取任务定义缓存失败，走 DB", e);
            return null;
        }
    }

    /**
     * 写入已启用任务定义
     */
    public void writeEnabledTasks(List<CoupleTask> tasks) {
        if (tasks == null) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(TASK_DEF_KEY,
                    objectMapper.writeValueAsString(tasks), MAX_TTL);
        } catch (Exception e) {
            log.warn("[情侣任务] 写入任务定义缓存失败", e);
        }
    }

    /**
     * 失效任务定义缓存
     */
    public void evictEnabledTasks() {
        try {
            redisTemplate.delete(TASK_DEF_KEY);
        } catch (Exception e) {
            log.warn("[情侣任务] 任务定义缓存失效失败", e);
        }
    }

    /**
     * 缓存有效期：1 小时与「距业务日零点」取较小值
     */
    private Duration resolveTtl() {
        long seconds = Math.min(MAX_TTL.getSeconds(), businessDateUtils.secondsUntilNextBusinessDay());
        return Duration.ofSeconds(Math.max(1L, seconds));
    }
}
