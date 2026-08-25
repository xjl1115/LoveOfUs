package com.example.lovemap.config;

import com.example.lovemap.common.constant.AnniversaryConstant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 纪念日缓存清理定时任务
 * 每天凌晨 0 点自动清理纪念日相关缓存
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnniversaryCacheCleanupTask {

    private final StringRedisTemplate redisTemplate;

    @Scheduled(cron = "0 0 0 * * ?")
    public void cleanAnniversaryCache() {
        try {
            Set<String> listKeys = redisTemplate.keys(AnniversaryConstant.ANNIVERSARY_LIST_PREFIX + "*");
            if (listKeys != null && !listKeys.isEmpty()) {
                redisTemplate.delete(listKeys);
            }
            Set<String> detailKeys = redisTemplate.keys(AnniversaryConstant.ANNIVERSARY_DETAIL_PREFIX + "*");
            if (detailKeys != null && !detailKeys.isEmpty()) {
                redisTemplate.delete(detailKeys);
            }
        } catch (Exception e) {
            log.error("清理纪念日缓存失败", e);
        }
    }
}
