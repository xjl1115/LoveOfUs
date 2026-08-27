package com.example.lovemap.ai.tool;

import com.example.lovemap.ai.context.AiUserContext;
import com.example.lovemap.common.Result;
import com.example.lovemap.model.vo.AlbumVO;
import com.example.lovemap.model.vo.AnniversaryVO;
import com.example.lovemap.model.vo.TimelineGroupVO;
import com.example.lovemap.model.vo.TimelinePhotoVO;
import com.example.lovemap.model.vo.TimelineResultVO;
import com.example.lovemap.model.vo.UserStatsVO;
import com.example.lovemap.model.vo.UserVO;
import com.example.lovemap.service.AlbumService;
import com.example.lovemap.service.AnniversaryService;
import com.example.lovemap.service.PhotoService;
import com.example.lovemap.service.UserService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 回忆报告 AI 工具
 * <p>
 * 为 LLM 提供结构化的"回忆素材包"，让 LLM 用自然语言组织成月度/年度回忆报告。
 * 工具本身只做数据汇总，不写数据库。
 * <p>
 * 工具方法：
 * <ul>
 *   <li>generateMonthlyReport —— 月度回忆：照片/相册/纪念日/城市分布/亮点照片</li>
 *   <li>generateYearlyReport  —— 年度回忆：照片趋势/Top城市/Top月份/Top相册</li>
 *   <li>getCoupleMilestones   —— 里程碑：在一起 N 天 / 即将到来的纪念日</li>
 * </ul>
 * <p>
 * 所有方法只读，无副作用；走 Service 层自动按 groupId 隔离。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MemoryReportTool {

    private final UserService userService;
    private final AnniversaryService anniversaryService;
    private final AlbumService albumService;
    private final PhotoService photoService;

    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final int TIMELINE_FETCH_SIZE = 200;

    // ==================== 月度报告 ====================

    /**
     * 生成某个月份的回忆报告（默认当月）
     * <p>
     * 聚合指标：照片数、相册数、新增纪念日、去过的城市、亮点时刻（按月时间线 + 用户统计）。
     */
    @Tool("生成某个月的回忆报告（照片/相册/纪念日/城市分布等）。默认当月，yearMonth 格式 yyyy-MM。")
    public Map<String, Object> generateMonthlyReport(
            @P("月份 yyyy-MM，例如 2026-08；传 null 表示当月") String yearMonth) {

        Long userId = AiUserContext.requireUserId();
        YearMonth ym = parseYearMonth(yearMonth);
        if (ym == null) {
            return Map.of("error", "月份格式错误，应为 yyyy-MM：" + yearMonth);
        }
        LocalDate start = ym.atDay(1);
        LocalDate end = ym.atEndOfMonth();
        log.info("[AI-TOOL] generateMonthlyReport userId={}, yearMonth={}", userId, ym);

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("type", "monthly");
        report.put("period", ym.format(MONTH_FMT));
        report.put("rangeStart", start.toString());
        report.put("rangeEnd", end.toString());

        try {
            // 1. 用户基础信息 + 在一起天数
            fillUserBasics(userId, report);

            // 2. 当月照片时间线
            TimelineResultVO timeline = fetchTimeline(userId, start, end);
            List<TimelinePhotoVO> monthPhotos = flattenPhotos(timeline);
            report.put("photoCount", monthPhotos.size());
            report.put("cityCount", countDistinct(monthPhotos, TimelinePhotoVO::getCity));
            report.put("provinceCount", countDistinct(monthPhotos, TimelinePhotoVO::getProvence));

            // 3. 城市 Top5
            report.put("topCities", topNByCount(monthPhotos, TimelinePhotoVO::getCity, 5));
            report.put("topProvinces", topNByCount(monthPhotos, TimelinePhotoVO::getProvence, 5));

            // 4. 当月新建的相册
            List<Map<String, Object>> newAlbums = newAlbumsInMonth(userId, start, end);
            report.put("newAlbumCount", newAlbums.size());
            report.put("newAlbums", newAlbums);

            // 5. 当月纪念日（含本月到达的"反复性纪念日"）
            List<Map<String, Object>> anniversaries = anniversariesInMonth(userId, ym);
            report.put("anniversaryCount", anniversaries.size());
            report.put("anniversaries", anniversaries);

            // 6. 亮点时刻（按日聚合，挑拍摄日期有照片的几天）
            report.put("highlightDays", highlightDays(monthPhotos, 5));

            return report;
        } catch (Exception e) {
            log.error("[AI-TOOL] generateMonthlyReport 失败", e);
            return Map.of("error", "生成月度报告失败：" + e.getMessage());
        }
    }

    // ==================== 年度报告 ====================

    /**
     * 生成某年度的回忆报告（默认今年）
     */
    @Tool("生成某年的回忆报告（按月聚合照片/相册/纪念日/城市）。year 传 null 表示今年。")
    public Map<String, Object> generateYearlyReport(
            @P("年份 yyyy，例如 2026；传 null 表示今年") Integer year) {

        Long userId = AiUserContext.requireUserId();
        int actualYear = (year == null) ? LocalDate.now().getYear() : year;
        LocalDate start = LocalDate.of(actualYear, 1, 1);
        LocalDate end = LocalDate.of(actualYear, 12, 31);
        log.info("[AI-TOOL] generateYearlyReport userId={}, year={}", userId, actualYear);

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("type", "yearly");
        report.put("period", String.valueOf(actualYear));
        report.put("rangeStart", start.toString());
        report.put("rangeEnd", end.toString());

        try {
            // 1. 基础信息
            fillUserBasics(userId, report);

            // 2. 全年照片
            TimelineResultVO timeline = fetchTimeline(userId, start, end);
            List<TimelinePhotoVO> yearPhotos = flattenPhotos(timeline);
            report.put("photoCount", yearPhotos.size());

            // 3. 按月分布（1~12 月的照片数）
            Map<String, Integer> monthDist = new LinkedHashMap<>();
            for (int m = 1; m <= 12; m++) {
                monthDist.put(String.format("%02d", m), 0);
            }
            for (TimelinePhotoVO p : yearPhotos) {
                if (p.getTakenDate() == null || p.getTakenDate().length() < 7) continue;
                String mm = p.getTakenDate().substring(5, 7);
                monthDist.merge(mm, 1, Integer::sum);
            }
            report.put("monthDistribution", monthDist);

            // 4. Top 城市 / Top 省份 / Top 相册
            report.put("topCities", topNByCount(yearPhotos, TimelinePhotoVO::getCity, 10));
            report.put("topProvinces", topNByCount(yearPhotos, TimelinePhotoVO::getProvence, 10));

            // 5. Top 相册（按相册内照片数）
            report.put("topAlbums", topAlbums(userId, 5));

            // 6. 当年纪念日
            YearMonth yearStart = YearMonth.of(actualYear, 1);
            List<Map<String, Object>> anniversaries = anniversariesInYear(userId, actualYear);
            report.put("anniversaryCount", anniversaries.size());
            report.put("anniversaries", anniversaries);

            // 7. 与去年对比（如果去年有数据）
            Integer prevYearPhotoCount = photoCountInYear(userId, actualYear - 1);
            report.put("previousYearPhotoCount", prevYearPhotoCount);
            if (prevYearPhotoCount > 0 && yearPhotos.size() > 0) {
                double ratio = (double) yearPhotos.size() / prevYearPhotoCount;
                report.put("yoyRatio", Math.round(ratio * 100.0) / 100.0);
            } else {
                report.put("yoyRatio", null);
            }

            return report;
        } catch (Exception e) {
            log.error("[AI-TOOL] generateYearlyReport 失败", e);
            return Map.of("error", "生成年度报告失败：" + e.getMessage());
        }
    }

    // ==================== 里程碑 ====================

    /**
     * 情侣里程碑汇总
     * <p>
     * 包含：在一起天数、最近纪念日、未来 30 天纪念日、整体统计。
     */
    @Tool("汇总情侣里程碑：在一起天数、最近纪念日、未来 30 天纪念日、整体照片/相册/城市统计。")
    public Map<String, Object> getCoupleMilestones() {
        Long userId = AiUserContext.requireUserId();
        log.info("[AI-TOOL] getCoupleMilestones userId={}", userId);
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("type", "milestones");

        try {
            fillUserBasics(userId, report);

            // 未来 30 天纪念日
            Result<List<AnniversaryVO>> annResult = anniversaryService.listAnniversaries(userId.intValue());
            List<AnniversaryVO> all = (annResult != null && annResult.getData() != null)
                    ? annResult.getData() : List.of();
            LocalDate today = LocalDate.now();
            LocalDate horizon = today.plusDays(30);

            List<Map<String, Object>> upcoming = new ArrayList<>();
            List<Map<String, Object>> past = new ArrayList<>();
            for (AnniversaryVO vo : all) {
                vo.calculateDaysUntil();
                LocalDate occurrence = nextOccurrence(vo, today);
                if (occurrence == null) continue;
                Map<String, Object> item = new HashMap<>();
                item.put("name", vo.getName());
                item.put("anniversaryDate", vo.getAnniversaryDate() != null ? vo.getAnniversaryDate().toString() : null);
                item.put("nextOccurrence", occurrence.toString());
                item.put("daysUntil", ChronoUnit.DAYS.between(today, occurrence));
                item.put("recurring", vo.getIsRecurring());
                if (!occurrence.isBefore(today) && !occurrence.isAfter(horizon)) {
                    upcoming.add(item);
                } else if (occurrence.isBefore(today)) {
                    past.add(item);
                }
            }
            // 排序：即将到来的按天数升序；已过去的按天数降序（最近的在前）
            upcoming.sort((a, b) -> Long.compare((long) a.get("daysUntil"), (long) b.get("daysUntil")));
            past.sort((a, b) -> Long.compare((long) b.get("daysUntil"), (long) a.get("daysUntil")));
            report.put("upcomingAnniversaries", upcoming);
            report.put("recentAnniversaries", past.size() > 5 ? past.subList(0, 5) : past);

            // 整体统计
            Result<UserStatsVO> statsResult = userService.getUserStats(userId.intValue());
            if (statsResult != null && statsResult.getData() != null) {
                UserStatsVO s = statsResult.getData();
                Map<String, Object> stats = new LinkedHashMap<>();
                stats.put("photoCount", s.getPhotoCount());
                stats.put("albumCount", s.getAlbumCount());
                stats.put("cityCount", s.getCityCount());
                stats.put("daysTogether", s.getDaysTogether());
                report.put("stats", stats);
            }
            return report;
        } catch (Exception e) {
            log.error("[AI-TOOL] getCoupleMilestones 失败", e);
            return Map.of("error", "汇总里程碑失败：" + e.getMessage());
        }
    }

    // ==================== 内部：数据组装 ====================

    /**
     * 注入"昵称/伴侣昵称/在一起天数/关系开始日期"
     */
    private void fillUserBasics(Long userId, Map<String, Object> report) {
        Result<UserVO> userResult = userService.getUserInfo(userId.intValue());
        if (userResult == null || userResult.getData() == null) return;
        UserVO user = userResult.getData();
        report.put("selfNickname", user.getNickname());
        report.put("partnerNickname", user.getPartner() != null ? user.getPartner().getNickname() : null);
        report.put("daysTogether", user.getDaysTogether());
        report.put("relationshipStart",
                user.getRelationshipStart() != null ? user.getRelationshipStart().toString() : null);
        report.put("isBound", user.getIsBound());
    }

    /**
     * 拉取时间线照片（自动按 groupId 隔离）
     */
    private TimelineResultVO fetchTimeline(Long userId, LocalDate start, LocalDate end) {
        Result<TimelineResultVO> result = photoService.getTimeline(
                userId.intValue(), 1, TIMELINE_FETCH_SIZE, null, null, start, end);
        return (result != null && result.getData() != null) ? result.getData() : new TimelineResultVO();
    }

    private List<TimelinePhotoVO> flattenPhotos(TimelineResultVO timeline) {
        if (timeline.getRecords() == null) return List.of();
        List<TimelinePhotoVO> out = new ArrayList<>();
        for (TimelineGroupVO g : timeline.getRecords()) {
            if (g.getPhotos() != null) out.addAll(g.getPhotos());
        }
        return out;
    }

    /**
     * 统计某字段的不同值数量
     */
    private int countDistinct(List<TimelinePhotoVO> photos,
                               java.util.function.Function<TimelinePhotoVO, String> getter) {
        return (int) photos.stream()
                .map(getter)
                .filter(s -> s != null && !s.isBlank())
                .distinct()
                .count();
    }

    /**
     * TopN 维度统计，例如 Top 城市
     */
    private List<Map<String, Object>> topNByCount(List<TimelinePhotoVO> photos,
                                                  java.util.function.Function<TimelinePhotoVO, String> getter,
                                                  int n) {
        Map<String, Integer> counts = new HashMap<>();
        for (TimelinePhotoVO p : photos) {
            String v = getter.apply(p);
            if (v == null || v.isBlank()) continue;
            counts.merge(v, 1, Integer::sum);
        }
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(counts.entrySet());
        sorted.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        List<Map<String, Object>> out = new ArrayList<>();
        for (int i = 0; i < Math.min(n, sorted.size()); i++) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", sorted.get(i).getKey());
            item.put("count", sorted.get(i).getValue());
            out.add(item);
        }
        return out;
    }

    /**
     * 月内新建的相册
     */
    private List<Map<String, Object>> newAlbumsInMonth(Long userId, LocalDate start, LocalDate end) {
        Result<List<AlbumVO>> result = albumService.listAlbums(userId.intValue());
        if (result == null || result.getData() == null) return List.of();
        List<Map<String, Object>> out = new ArrayList<>();
        for (AlbumVO a : result.getData()) {
            if (a.getCreatedAt() == null) continue;
            LocalDate created = a.getCreatedAt().toLocalDate();
            if (!created.isBefore(start) && !created.isAfter(end)) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", a.getId());
                item.put("name", a.getName());
                item.put("photoCount", a.getPhotoCount());
                item.put("createdAt", created.toString());
                out.add(item);
            }
        }
        return out;
    }

    /**
     * 月内纪念日
     * <p>
     * 包含两类：
     * 1. 一次性纪念日本身落在该月
     * 2. 反复性纪念日在该月有"下一次发生"
     */
    private List<Map<String, Object>> anniversariesInMonth(Long userId, YearMonth ym) {
        Result<List<AnniversaryVO>> result = anniversaryService.listAnniversaries(userId.intValue());
        if (result == null || result.getData() == null) return List.of();
        List<Map<String, Object>> out = new ArrayList<>();
        for (AnniversaryVO vo : result.getData()) {
            vo.calculateDaysUntil();
            LocalDate occurrence = nextOccurrence(vo, LocalDate.now());
            if (occurrence == null) continue;
            if (YearMonth.from(occurrence).equals(ym)) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("name", vo.getName());
                item.put("originalDate", vo.getAnniversaryDate() != null ? vo.getAnniversaryDate().toString() : null);
                item.put("occurrenceDate", occurrence.toString());
                item.put("recurring", vo.getIsRecurring());
                item.put("daysUntil", vo.getDaysUntil());
                out.add(item);
            }
        }
        out.sort((a, b) -> Long.compare((long) a.get("daysUntil"), (long) b.get("daysUntil")));
        return out;
    }

    /**
     * 年内所有纪念日
     */
    private List<Map<String, Object>> anniversariesInYear(Long userId, int year) {
        Result<List<AnniversaryVO>> result = anniversaryService.listAnniversaries(userId.intValue());
        if (result == null || result.getData() == null) return List.of();
        List<Map<String, Object>> out = new ArrayList<>();
        for (AnniversaryVO vo : result.getData()) {
            vo.calculateDaysUntil();
            LocalDate occurrence = nextOccurrence(vo, LocalDate.now());
            if (occurrence == null || occurrence.getYear() != year) continue;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", vo.getName());
            item.put("originalDate", vo.getAnniversaryDate() != null ? vo.getAnniversaryDate().toString() : null);
            item.put("occurrenceDate", occurrence.toString());
            item.put("recurring", vo.getIsRecurring());
            out.add(item);
        }
        out.sort((a, b) -> String.valueOf(a.get("occurrenceDate")).compareTo(String.valueOf(b.get("occurrenceDate"))));
        return out;
    }

    /**
     * 计算"下一次发生的日期"：反复性纪念日返回本年（或明年）对应日期；一次性返回原日期（过去也照原值返回）。
     */
    private LocalDate nextOccurrence(AnniversaryVO vo, LocalDate today) {
        if (vo.getAnniversaryDate() == null) return null;
        if (!Boolean.TRUE.equals(vo.getIsRecurring())) {
            return vo.getAnniversaryDate();
        }
        LocalDate candidate = vo.getAnniversaryDate().withYear(today.getYear());
        if (candidate.isBefore(today) || candidate.isEqual(today)) {
            candidate = candidate.plusYears(1);
        }
        return candidate;
    }

    /**
     * 亮点日：按拍摄日期聚合，返回 TopN 照片多的日期
     */
    private List<Map<String, Object>> highlightDays(List<TimelinePhotoVO> photos, int topN) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (TimelinePhotoVO p : photos) {
            if (p.getTakenDate() == null || p.getTakenDate().length() < 10) continue;
            String day = p.getTakenDate().substring(0, 10);
            counts.merge(day, 1, Integer::sum);
        }
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(counts.entrySet());
        sorted.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        List<Map<String, Object>> out = new ArrayList<>();
        for (int i = 0; i < Math.min(topN, sorted.size()); i++) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", sorted.get(i).getKey());
            item.put("photoCount", sorted.get(i).getValue());
            out.add(item);
        }
        return out;
    }

    /**
     * Top 相册（按 photoCount 排序）
     */
    private List<Map<String, Object>> topAlbums(Long userId, int n) {
        Result<List<AlbumVO>> result = albumService.listAlbums(userId.intValue());
        if (result == null || result.getData() == null) return List.of();
        List<AlbumVO> all = new ArrayList<>(result.getData());
        all.sort((a, b) -> Integer.compare(
                b.getPhotoCount() == null ? 0 : b.getPhotoCount(),
                a.getPhotoCount() == null ? 0 : a.getPhotoCount()));
        List<Map<String, Object>> out = new ArrayList<>();
        for (int i = 0; i < Math.min(n, all.size()); i++) {
            AlbumVO a = all.get(i);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", a.getId());
            item.put("name", a.getName());
            item.put("photoCount", a.getPhotoCount());
            item.put("coverUrl", a.getCoverPhotoUrl());
            out.add(item);
        }
        return out;
    }

    /**
     * 计算某年的照片总数（仅按日期范围调一次时间线）
     */
    private Integer photoCountInYear(Long userId, int year) {
        LocalDate start = LocalDate.of(year, 1, 1);
        LocalDate end = LocalDate.of(year, 12, 31);
        TimelineResultVO timeline = fetchTimeline(userId, start, end);
        return flattenPhotos(timeline).size();
    }

    /**
     * 解析 yyyy-MM
     */
    private YearMonth parseYearMonth(String s) {
        if (s == null || s.isBlank()) return YearMonth.now();
        try {
            return YearMonth.parse(s.trim(), MONTH_FMT);
        } catch (Exception e) {
            return null;
        }
    }
}