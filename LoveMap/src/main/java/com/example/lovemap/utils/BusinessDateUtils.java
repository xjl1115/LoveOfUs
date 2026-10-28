package com.example.lovemap.utils;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;

/**
 * 业务日期封装
 *
 * 统一「零点 / 当天」的口径：任务周期按业务时区（默认 Asia/Shanghai）计算，
 * 禁止各处直接使用 {@link LocalDate#now()}，否则会出现「有人按 16 日、有人按 17 日」的错乱。
 *
 * 若服务器 / 数据库为 UTC，业务日期仍取 UTC+8 的日期。
 */
@Component
public class BusinessDateUtils {

    /** 周期键格式：yyyy-MM-dd（CHAR(10)，周/月取周期起始日） */
    private static final DateTimeFormatter PERIOD_KEY_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** 业务时区，默认 Asia/Shanghai */
    @Value("${points.task.business-zone:Asia/Shanghai}")
    private String businessZone;

    /**
     * 当前业务日期（按业务时区）
     */
    public LocalDate currentBusinessDate() {
        return LocalDate.now(resolveZone());
    }

    /**
     * 当前业务日期对应的周期键
     *
     * @param periodType daily/weekly/monthly
     */
    public String currentPeriodKey(String periodType) {
        return periodKey(currentBusinessDate(), periodType);
    }

    /**
     * 计算某一天所属周期的键
     *   daily   → 当天，如 2026-10-17
     *   weekly  → 本周周一，如 2026-10-12
     *   monthly → 当月 1 号，如 2026-10-01
     *
     * @param date       业务日期
     * @param periodType 周期类型，空或未知按 daily 处理
     */
    public String periodKey(LocalDate date, String periodType) {
        LocalDate base = date == null ? currentBusinessDate() : date;
        if (periodType == null) {
            return base.format(PERIOD_KEY_FORMATTER);
        }
        return switch (periodType) {
            case "weekly" -> base.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                    .format(PERIOD_KEY_FORMATTER);
            case "monthly" -> base.withDayOfMonth(1).format(PERIOD_KEY_FORMATTER);
            default -> base.format(PERIOD_KEY_FORMATTER);
        };
    }

    /**
     * 解析周期键为业务日期（解析失败时返回 null）
     */
    public LocalDate parsePeriodKey(String periodKey) {
        if (periodKey == null || periodKey.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(periodKey, PERIOD_KEY_FORMATTER);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 距下一个业务日零点的秒数
     *
     * 用于设置缓存过期时间：跨零点后昨日状态应自动失效，
     * 无需依赖定时任务清理缓存。
     */
    public long secondsUntilNextBusinessDay() {
        ZoneId zone = resolveZone();
        ZonedDateTime now = ZonedDateTime.now(zone);
        ZonedDateTime nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay(zone);
        return Math.max(1L, Duration.between(now, nextMidnight).getSeconds());
    }

    private ZoneId resolveZone() {
        try {
            return ZoneId.of(businessZone);
        } catch (Exception e) {
            // 配置了非法时区时退回默认业务时区，避免整个任务模块不可用
            return ZoneId.of("Asia/Shanghai");
        }
    }
}
