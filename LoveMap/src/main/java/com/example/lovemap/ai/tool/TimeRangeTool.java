package com.example.lovemap.ai.tool;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 时间范围 AI 工具
 * <p>
 * 解决小模型（qwen3.7-flash 等）"自作主张算错日期"的问题：
 * 提供"今天/最近 N 天/本月/上月/本年"等日期范围的真实计算结果，
 * 供 LLM 在调用 searchPhotos / MemoryReportTool 等需要日期范围的工具前先查询。
 * <p>
 * 所有方法**只读、纯函数**，不涉及用户上下文，无副作用。
 */
@Slf4j
@Component
public class TimeRangeTool {

    /** 上海时区，与 application.yml system-prompt 中标注一致 */
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE;

    /**
     * 返回服务器当前日期
     */
    @Tool("返回服务器当前日期（Asia/Shanghai），格式 yyyy-MM-dd。用于在调用 searchPhotos/MemoryReportTool 等需要日期范围的工具之前获取准确日期。")
    public Map<String, Object> today() {
        LocalDate now = LocalDate.now(ZONE);
        log.info("[AI-TOOL] today -> {}", now);
        return baseResult(now);
    }

    /**
     * 更直白的"当前时间"工具——返回服务器时间与星期，
     * LLM 问"今天几号/今天周几"时应优先调用本工具，
     * 不要凭空算日期。
     */
    @Tool("获取服务器当前时间（Asia/Shanghai 时区）。LLM 不要凭空猜日期，凡涉及'今天/现在/还有几天'都应先调用本工具。")
    public Map<String, Object> getCurrentTime() {
        LocalDate now = LocalDate.now(ZONE);
        Map<String, Object> r = baseResult(now);
        // 星期几的中文
        r.put("weekdayCn", cnWeekday(now.getDayOfWeek()));
        // 距今年结束
        LocalDate yearEnd = LocalDate.of(now.getYear(), 12, 31);
        r.put("daysUntilYearEnd", java.time.temporal.ChronoUnit.DAYS.between(now, yearEnd));
        // 距本月结束
        LocalDate monthEnd = now.withDayOfMonth(now.lengthOfMonth());
        r.put("daysUntilMonthEnd", java.time.temporal.ChronoUnit.DAYS.between(now, monthEnd));
        return r;
    }

    private String cnWeekday(DayOfWeek dw) {
        return switch (dw) {
            case MONDAY -> "星期一";
            case TUESDAY -> "星期二";
            case WEDNESDAY -> "星期三";
            case THURSDAY -> "星期四";
            case FRIDAY -> "星期五";
            case SATURDAY -> "星期六";
            case SUNDAY -> "星期日";
        };
    }

    /**
     * 返回"最近 N 天"的起止日期（含今天）。
     * <p>
     * 例：今天是 2026-08-27，days=7 → 返回 2026-08-21 ~ 2026-08-27。
     */
    @Tool("返回最近 N 天的日期范围（包含今天）。例如 days=7 表示最近一周。")
    public Map<String, Object> recentDays(@P("天数，1-365") Integer days) {
        if (days == null || days <= 0) {
            return Map.of("error", "days 必须为正整数");
        }
        if (days > 365) {
            return Map.of("error", "days 不能超过 365");
        }
        LocalDate today = LocalDate.now(ZONE);
        LocalDate start = today.minusDays(days - 1L);
        log.info("[AI-TOOL] recentDays days={} -> {} ~ {}", days, start, today);
        Map<String, Object> r = baseResult(today);
        r.put("startDate", start.format(ISO));
        r.put("endDate", today.format(ISO));
        r.put("days", days);
        r.put("description", "最近 " + days + " 天（" + start + " ~ " + today + "，含今天）");
        return r;
    }

    /**
     * 返回固定时间段的日期范围：
     * <ul>
     *   <li>本周 / 上周 / 本月 / 上月 / 本季度 / 上季度 / 本年 / 上年</li>
     *   <li>今天 / 昨天</li>
     * </ul>
     */
    @Tool("返回固定时间段的日期范围。支持：today/today/yesterday/thisWeek/lastWeek/thisMonth/lastMonth/thisQuarter/lastQuarter/thisYear/lastYear")
    public Map<String, Object> monthRange(@P("时间段标识，例如 thisMonth / lastMonth / thisYear / yesterday") String period) {
        if (period == null || period.isBlank()) {
            return Map.of("error", "period 不能为空；支持：today/yesterday/thisWeek/lastWeek/thisMonth/lastMonth/thisQuarter/lastQuarter/thisYear/lastYear");
        }
        String key = period.trim();
        LocalDate today = LocalDate.now(ZONE);
        LocalDate start, end;
        String label;

        switch (key) {
            case "today" -> {
                start = today;
                end = today;
                label = "今天";
            }
            case "yesterday" -> {
                LocalDate y = today.minusDays(1);
                start = y;
                end = y;
                label = "昨天";
            }
            case "thisWeek" -> {
                // 周一到周日
                LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                start = monday;
                end = monday.plusDays(6);
                label = "本周";
            }
            case "lastWeek" -> {
                LocalDate thisMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                LocalDate lastMonday = thisMonday.minusDays(7);
                start = lastMonday;
                end = lastMonday.plusDays(6);
                label = "上周";
            }
            case "thisMonth" -> {
                YearMonth ym0 = YearMonth.from(today);
                start = ym0.atDay(1);
                end = ym0.atEndOfMonth();
                label = "本月";
            }
            case "lastMonth" -> {
                YearMonth ym0 = YearMonth.from(today).minusMonths(1);
                start = ym0.atDay(1);
                end = ym0.atEndOfMonth();
                label = "上月";
            }
            case "thisQuarter" -> {
                int q = (today.getMonthValue() - 1) / 3;
                LocalDate qStart = LocalDate.of(today.getYear(), q * 3 + 1, 1);
                LocalDate qEnd = qStart.plusMonths(3).minusDays(1);
                start = qStart;
                end = qEnd;
                label = "本季度";
            }
            case "lastQuarter" -> {
                int q = (today.getMonthValue() - 1) / 3;
                LocalDate qStart = LocalDate.of(today.getYear(), q * 3 + 1, 1).minusMonths(3);
                LocalDate qEnd = qStart.plusMonths(3).minusDays(1);
                start = qStart;
                end = qEnd;
                label = "上季度";
            }
            case "thisYear" -> {
                start = LocalDate.of(today.getYear(), 1, 1);
                end = LocalDate.of(today.getYear(), 12, 31);
                label = "本年";
            }
            case "lastYear" -> {
                int y0 = today.getYear() - 1;
                start = LocalDate.of(y0, 1, 1);
                end = LocalDate.of(y0, 12, 31);
                label = "去年";
            }
            default -> {
                return Map.of(
                        "error", "不支持的 period: " + key,
                        "supported", "today/yesterday/thisWeek/lastWeek/thisMonth/lastMonth/thisQuarter/lastQuarter/thisYear/lastYear"
                );
            }
        }

        log.info("[AI-TOOL] monthRange period={} -> {} ~ {}", key, start, end);
        Map<String, Object> r = baseResult(today);
        r.put("period", key);
        r.put("startDate", start.format(ISO));
        r.put("endDate", end.format(ISO));
        r.put("description", label + "（" + start + " ~ " + end + "）");
        return r;
    }

    /**
     * 公共字段：当前日期 + 时区
     */
    private Map<String, Object> baseResult(LocalDate today) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("today", today.format(ISO));
        r.put("weekday", today.getDayOfWeek().name());
        r.put("zone", ZONE.getId());
        return r;
    }
}