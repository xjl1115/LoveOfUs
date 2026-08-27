package com.example.lovemap.ai.tool;

import com.example.lovemap.ai.context.AiUserContext;
import com.example.lovemap.common.Result;
import com.example.lovemap.mapper.UserMapper;
import com.example.lovemap.model.entity.Anniversary;
import com.example.lovemap.model.entity.AnniversaryReminder;
import com.example.lovemap.model.vo.AnniversaryVO;
import com.example.lovemap.service.AnniversaryReminderService;
import com.example.lovemap.service.AnniversaryService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 纪念日提醒 AI 工具（P0）
 * <p>
 * - listReminders                : 查询某纪念日的提醒计划
 * - prepareSetReminder           : 二次确认第一步
 * - confirmSetReminder           : 二次确认第二步（真正写入）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReminderTool {

    private final AnniversaryService anniversaryService;
    private final AnniversaryReminderService reminderService;
    private final UserMapper userMapper;

    /** 二次确认缓存 */
    private final Map<String, PendingReminder> pendingMap = new ConcurrentHashMap<>();

    /**
     * 查询某纪念日的提醒计划
     */
    @Tool("查询某个纪念日的提醒计划（提前几天提醒的具体日期与是否已发送）。name 模糊匹配。")
    public List<Map<String, Object>> listReminders(@P("纪念日名称") String name) {
        Long userId = AiUserContext.requireUserId();
        log.info("[AI-TOOL] listReminders userId={}, name='{}'", userId, name);
        try {
            Result<List<AnniversaryVO>> r = anniversaryService.listAnniversaries(userId.intValue());
            if (r == null || r.getData() == null) return List.of();
            String kw = name == null ? "" : name.trim().toLowerCase();
            List<Map<String, Object>> out = new ArrayList<>();
            for (AnniversaryVO vo : r.getData()) {
                if (vo.getName() == null) continue;
                if (!kw.isEmpty() && !vo.getName().toLowerCase().contains(kw)) continue;
                List<AnniversaryReminder> list = reminderService.listByAnniversary(vo.getId());
                List<Map<String, Object>> rs = new ArrayList<>();
                for (AnniversaryReminder rem : list) {
                    Map<String, Object> m = new HashMap<>();
                    m.put("remindDate", rem.getRemindDate() != null ? rem.getRemindDate().toString() : null);
                    m.put("remindDays", rem.getRemindDays());
                    m.put("sent", rem.getIsSent() != null && rem.getIsSent() == 1);
                    rs.add(m);
                }
                Map<String, Object> entry = new HashMap<>();
                entry.put("anniversaryId", vo.getId());
                entry.put("name", vo.getName());
                entry.put("date", vo.getAnniversaryDate() != null ? vo.getAnniversaryDate().toString() : null);
                entry.put("reminders", rs);
                out.add(entry);
            }
            return out;
        } catch (Exception e) {
            log.error("[AI-TOOL] listReminders 失败", e);
            return List.of(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 创建提醒计划（第一步：返回 token）
     */
    @Tool("为某个纪念日设置提前提醒（第一步，返回确认 token）。提前几天可指定，例如 7/3/1。")
    public Map<String, Object> prepareSetReminder(
            @P("纪念日名称（模糊匹配）") String name,
            @P("提前几天提醒，多个用英文逗号分隔，如 \"7,3,1\"") String remindDays) {
        Long userId = AiUserContext.requireUserId();
        if (name == null || name.isBlank()) return Map.of("error", "纪念日名称不能为空");

        List<Integer> days = parseRemindDays(remindDays);
        if (days.isEmpty()) return Map.of("error", "remindDays 解析失败，应为正整数列表，如 7,3,1");

        try {
            Result<List<AnniversaryVO>> r = anniversaryService.listAnniversaries(userId.intValue());
            if (r == null || r.getData() == null) return Map.of("error", "未找到纪念日");
            AnniversaryVO match = null;
            String kw = name.trim().toLowerCase();
            for (AnniversaryVO vo : r.getData()) {
                if (vo.getName() != null && vo.getName().toLowerCase().contains(kw)) {
                    match = vo;
                    break;
                }
            }
            if (match == null) return Map.of("error", "未找到纪念日：" + name);

            String token = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            pendingMap.put(token, new PendingReminder(userId.intValue(), match.getId(), days));
            new java.util.Timer().schedule(new java.util.TimerTask() {
                @Override public void run() { pendingMap.remove(token); }
            }, 60 * 60 * 1000L);

            return Map.of(
                    "status", "CONFIRM_REQUIRED",
                    "confirm_token", token,
                    "preview", Map.of(
                            "anniversaryName", match.getName(),
                            "anniversaryDate", match.getAnniversaryDate() != null ? match.getAnniversaryDate().toString() : null,
                            "remindDays", days),
                    "hint", "请向用户复述以上内容并请求确认；用户确认后调用 confirmSetReminder(token='"
                            + token + "')"
            );
        } catch (Exception e) {
            log.error("[AI-TOOL] prepareSetReminder 失败", e);
            return Map.of("error", e.getMessage());
        }
    }

    /**
     * 创建提醒计划（第二步：真正写入）
     */
    @Tool("为纪念日设置提醒（第二步，用户已确认后真正写入）。必须传入 prepareSetReminder 返回的 confirm_token。")
    public Map<String, Object> confirmSetReminder(@P("prepareSetReminder 返回的确认 token") String confirmToken) {
        Long userId = AiUserContext.requireUserId();
        if (confirmToken == null || confirmToken.isBlank()) return Map.of("error", "confirm_token 不能为空");
        PendingReminder pending = pendingMap.remove(confirmToken);
        if (pending == null) return Map.of("error", "确认凭证无效或已过期");
        if (!pending.userId.equals(userId.intValue())) return Map.of("error", "确认凭证归属错误");

        try {
            // 通过 AnniversaryService 拿完整实体（含 id 与 anniversaryDate）
            Anniversary anniv = new Anniversary();
            anniv.setId(pending.anniversaryId);
            // 需要 groupId
            Result<List<AnniversaryVO>> r = anniversaryService.listAnniversaries(userId.intValue());
            AnniversaryVO vo = null;
            if (r != null && r.getData() != null) {
                for (AnniversaryVO v : r.getData()) {
                    if (pending.anniversaryId.equals(v.getId())) {
                        vo = v;
                        break;
                    }
                }
            }
            if (vo == null) return Map.of("error", "纪念日已不存在");
            // vo 没有 groupId 字段，通过当前 user 反查
            com.example.lovemap.model.entity.User me = userMapper.selectById(userId.intValue());
            if (me == null || me.getGroupId() == null) return Map.of("error", "无法获取 groupId");
            anniv.setGroupId(me.getGroupId());
            anniv.setAnniversaryDate(vo.getAnniversaryDate());
            anniv.setRemindDays(pending.days.getFirst());
            anniv.setIsRecurring(vo.getIsRecurring());

            // 清理旧的（避免重复）
            reminderService.deleteByAnniversary(anniv.getId());
            List<AnniversaryReminder> created = reminderService.planReminders(anniv.getGroupId(), anniv);

            List<Map<String, Object>> rs = new ArrayList<>();
            for (AnniversaryReminder rem : created) {
                Map<String, Object> m = new HashMap<>();
                m.put("remindDate", rem.getRemindDate() != null ? rem.getRemindDate().toString() : null);
                m.put("remindDays", rem.getRemindDays());
                rs.add(m);
            }
            return Map.of(
                    "status", "SCHEDULED",
                    "anniversaryName", vo.getName(),
                    "reminders", rs
            );
        } catch (Exception e) {
            log.error("[AI-TOOL] confirmSetReminder 失败", e);
            return Map.of("error", "创建失败：" + e.getMessage());
        }
    }

    // ==================== 内部 ====================

    private List<Integer> parseRemindDays(String s) {
        List<Integer> list = new ArrayList<>();
        if (s == null) return list;
        for (String token : s.split(",")) {
            try {
                int v = Integer.parseInt(token.trim());
                if (v > 0 && v <= 60) list.add(v);
            } catch (NumberFormatException ignore) {
            }
        }
        return list;
    }

    private record PendingReminder(Integer userId, Long anniversaryId, List<Integer> days) {}
}