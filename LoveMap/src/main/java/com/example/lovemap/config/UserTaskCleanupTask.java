package com.example.lovemap.config;

import com.example.lovemap.mapper.UserTaskMapper;
import com.example.lovemap.utils.BusinessDateUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 用户任务历史清理
 *
 * 数据膨胀控制：日任务行按「日活 × 每日任务数」增长，
 * 每天凌晨清理保留期（默认 5 天）之外的日任务行。
 * 周 / 月任务行保留，不参与清理。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserTaskCleanupTask {

    private final UserTaskMapper userTaskMapper;
    private final BusinessDateUtils businessDateUtils;

    /** 日任务行保留天数，可通过配置覆盖 */
    @Value("${points.task.retention-days:5}")
    private int retentionDays;

    @Scheduled(cron = "${points.task.cleanup-cron:0 0 3 * * ?}")
    public void cleanExpiredDailyTasks() {
        try {
            LocalDate cutoff = businessDateUtils.currentBusinessDate().minusDays(retentionDays);
            String beforeKey = cutoff.toString();
            int deleted = userTaskMapper.deleteDailyBefore(beforeKey);
            log.info("[情侣任务] 清理日任务历史完成，保留 {} 天，删除 {} 行（period_key < {}）",
                    retentionDays, deleted, beforeKey);
        } catch (Exception e) {
            log.error("[情侣任务] 清理日任务历史失败", e);
        }
    }
}
