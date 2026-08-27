package com.example.lovemap.ai.tool;

import com.example.lovemap.ai.context.AiUserContext;
import com.example.lovemap.mapper.UserMapper;
import com.example.lovemap.model.entity.MoodLog;
import com.example.lovemap.model.entity.User;
import com.example.lovemap.service.MoodLogService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 心情打卡 AI 工具（P0）
 * <p>
 * - getTodayMood      : 查询今日（默认）双人心情
 * - recordMood        : 写入/覆盖当前用户今日心情（无二次确认：覆盖可逆）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MoodTool {

    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    private final MoodLogService moodLogService;
    private final UserMapper userMapper;

    /**
     * 查询某天（默认今天）的心情打卡，包含当前用户与伴侣
     */
    @Tool("查询某一天情侣双方的心情况。date 传 null 表示今天。返回双方昵称 + 心情 emoji + 评分（1-5）+ 备注。")
    public Map<String, Object> getTodayMood(@P("日期 yyyy-MM-dd；传 null 表示今天") String date) {
        Long userId = AiUserContext.requireUserId();
        Long groupId = AiUserContext.requireGroupId();
        log.info("[AI-TOOL] getTodayMood userId={}, date='{}'", userId, date);

        LocalDate target;
        try {
            target = (date == null || date.isBlank()) ? LocalDate.now() : LocalDate.parse(date.trim());
        } catch (Exception e) {
            return Map.of("error", "日期格式错误，应为 yyyy-MM-dd：" + date);
        }

        try {
            User me = userMapper.selectById(userId.intValue());
            if (me == null) return Map.of("error", "用户不存在");

            List<MoodLog> logs = moodLogService.getByDate(groupId, target);

            // 当前用户和伴侣的昵称
            String meNick = me.getNickname();
            String partnerNick = null;

            Map<String, Object> mine = null;
            Map<String, Object> partner = null;
            for (MoodLog m : logs) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("mood", m.getMood());
                entry.put("score", m.getMoodScore());
                entry.put("note", m.getNote());
                entry.put("loggedAt", m.getCreatedAt() != null ? m.getCreatedAt().toString() : null);
                if (m.getUserId().equals(userId)) {
                    mine = entry;
                } else if (partnerNick != null && m.getUserId().equals(me.getPartnerId())) {
                    partner = entry;
                }
            }

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("date", target.format(ISO_DATE));
            out.put("me", mine != null ? wrap(mine, meNick) : Map.of("logged", false, "nickname", meNick));
            out.put("partner", partner != null ? wrap(partner, partnerNick) : (partnerNick == null ? null : Map.of("logged", false, "nickname", partnerNick)));
            // 一周心情概览
            out.put("weeklyStats", weeklyStats(groupId, target));
            return out;
        } catch (Exception e) {
            log.error("[AI-TOOL] getTodayMood 失败", e);
            return Map.of("error", "查询失败：" + e.getMessage());
        }
    }

    /**
     * 记录当前用户今日心情（直接覆盖，无二次确认）
     */
    @Tool("记录当前用户某一天的心情（直接覆盖当天记录）。mood 取值：happy/love/excited/tired/sad/angry/peaceful。score 1-5。")
    public Map<String, Object> recordMood(@P("心情标识：happy/love/excited/tired/sad/angry/peaceful") String mood,
                                        @P("强度 1-5") Integer score,
                                        @P("一句话备注，最多 50 字") String note,
                                        @P("日期 yyyy-MM-dd；传 null 表示今天") String date) {
        Long userId = AiUserContext.requireUserId();
        Long groupId = AiUserContext.requireGroupId();
        if (mood == null || mood.isBlank()) return Map.of("error", "mood 不能为空");
        if (score == null || score < 1 || score > 5) return Map.of("error", "score 必须 1-5");

        LocalDate target;
        try {
            target = (date == null || date.isBlank()) ? LocalDate.now() : LocalDate.parse(date.trim());
        } catch (Exception e) {
            return Map.of("error", "日期格式错误，应为 yyyy-MM-dd：" + date);
        }

        try {
            MoodLog saved = moodLogService.record(groupId, userId,
                    mood.trim().toLowerCase(),
                    score,
                    note == null ? null : note.substring(0, Math.min(note.length(), 50)),
                    target);
            return Map.of(
                    "status", "RECORDED",
                    "id", saved.getId(),
                    "mood", saved.getMood(),
                    "score", saved.getMoodScore(),
                    "note", saved.getNote(),
                    "date", saved.getLogDate().toString()
            );
        } catch (Exception e) {
            log.error("[AI-TOOL] recordMood 失败", e);
            return Map.of("error", "记录失败：" + e.getMessage());
        }
    }

    // ==================== 内部 ====================

    private Map<String, Object> wrap(Map<String, Object> src, String nickname) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("logged", true);
        m.put("nickname", nickname);
        m.putAll(src);
        return m;
    }

    /**
     * 最近 7 天打卡天数
     */
    private Map<String, Object> weeklyStats(Long groupId, LocalDate target) {
        LocalDate start = target.minusDays(6);
        List<MoodLog> logs = moodLogService.getInRange(groupId, start, target);
        int days = 0;
        java.util.Set<LocalDate> uniq = new java.util.HashSet<>();
        for (MoodLog m : logs) uniq.add(m.getLogDate());
        days = uniq.size();

        Map<String, Object> r = new HashMap<>();
        r.put("loggedDays", days);
        r.put("totalLogs", logs.size());
        r.put("startDate", start.format(ISO_DATE));
        r.put("endDate", target.format(ISO_DATE));
        return r;
    }
}