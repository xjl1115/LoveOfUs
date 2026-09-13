package com.example.lovemap.config;

import com.example.lovemap.common.constant.AnniversaryConstant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 纪念日缓存清理定时任务
 * 每天凌晨 0 点自动清理纪念日相关缓存，并在应用启动时补跑一次
 * （缓存里存的是写入时刻算出的倒计时，跨天后必须重算）
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

    /**
     * 启动补跑：应用启动时再清理一次纪念日缓存。
     * <p>
     * Spring 的 cron 任务错过执行不会补跑，0 点服务掉线时这次清理就没了，
     * 缓存里的 daysUntil 会停留在昨天。清缓存本身没有副作用（下次请求回源重建），
     * 代价只是一次 KEYS + DEL，因此不做"当天是否已清理"的标记，每次启动都清一次。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void cleanAnniversaryCacheOnStartup() {
        log.info("启动补跑：清理纪念日缓存");
        cleanAnniversaryCache();
    }
}
