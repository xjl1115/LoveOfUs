package com.example.lovemap.ai.tool;

import com.example.lovemap.ai.context.AiUserContext;
import com.example.lovemap.common.Result;
import com.example.lovemap.model.dto.AnniversaryDTO;
import com.example.lovemap.model.vo.AnniversaryVO;
import com.example.lovemap.service.AnniversaryService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 纪念日 AI 工具
 * <p>
 * - anniversary_query / anniversary_search  : 读取
 * - anniversary_create                       : 写操作，需二次确认
 * <p>
 * 严格二次确认：工具方法返回 confirm_token，前端弹窗用户确认后回传 token，
 * anniversary_confirm_create(token) 才真正执行。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnniversaryTool {

    private final AnniversaryService anniversaryService;

    /** 简单内存存储：token -> 待创建的纪念日 DTO。仅在单次会话内有效。 */
    private final Map<String, PendingCreate> pendingMap = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 查询所有纪念日
     */
    @Tool("查询当前用户的所有纪念日列表（含距离下次纪念日的天数）。")
    public List<Map<String, Object>> queryAnniversaries() {
        Long userId = AiUserContext.requireUserId();
        log.info("[AI-TOOL] queryAnniversaries userId={}", userId);
        try {
            Result<List<AnniversaryVO>> result = anniversaryService.listAnniversaries(userId.intValue());
            if (result == null || result.getData() == null) return List.of();
            List<Map<String, Object>> out = new ArrayList<>();
            for (AnniversaryVO vo : result.getData()) {
                vo.calculateDaysUntil();
                out.add(toMap(vo));
            }
            return out;
        } catch (Exception e) {
            log.error("[AI-TOOL] queryAnniversaries 失败", e);
            return List.of(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 按名称查询纪念日（精确匹配优先，模糊回退）
     */
    @Tool("按名称查询纪念日。可模糊匹配。返回所有匹配项。")
    public List<Map<String, Object>> searchAnniversaryByName(@P("纪念日名称关键词") String name) {
        Long userId = AiUserContext.requireUserId();
        log.info("[AI-TOOL] searchAnniversaryByName userId={}, name='{}'", userId, name);
        try {
            Result<List<AnniversaryVO>> result = anniversaryService.listAnniversaries(userId.intValue());
            if (result == null || result.getData() == null) return List.of();
            String kw = (name == null ? "" : name.trim().toLowerCase());
            List<Map<String, Object>> out = new ArrayList<>();
            for (AnniversaryVO vo : result.getData()) {
                if (!kw.isEmpty() && vo.getName() != null
                        && !vo.getName().toLowerCase().contains(kw)) {
                    continue;
                }
                vo.calculateDaysUntil();
                out.add(toMap(vo));
            }
            return out;
        } catch (Exception e) {
            log.error("[AI-TOOL] searchAnniversaryByName 失败", e);
            return List.of(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 计算距某个纪念日还有多少天
     */
    @Tool("计算距离某个纪念日名称对应的纪念日还有多少天；返回名称、日期、剩余天数。")
    public Map<String, Object> getCountdownByName(@P("纪念日名称") String name) {
        Long userId = AiUserContext.requireUserId();
        log.info("[AI-TOOL] getCountdownByName userId={}, name='{}'", userId, name);
        try {
            Result<List<AnniversaryVO>> result = anniversaryService.listAnniversaries(userId.intValue());
            if (result == null || result.getData() == null) return Map.of("error", "无纪念日记录");
            String kw = (name == null ? "" : name.trim().toLowerCase());
            AnniversaryVO best = null;
            for (AnniversaryVO vo : result.getData()) {
                if (vo.getName() == null) continue;
                if (kw.isEmpty() || vo.getName().toLowerCase().contains(kw)) {
                    best = vo;
                    break;
                }
            }
            if (best == null) return Map.of("error", "未找到匹配纪念日：" + name);
            best.calculateDaysUntil();
            return toMap(best);
        } catch (Exception e) {
            log.error("[AI-TOOL] getCountdownByName 失败", e);
            return Map.of("error", e.getMessage());
        }
    }

    /**
     * 创建纪念日（**二次确认** 第一步）
     * <p>
     * 不真正写入 DB，而是返回 confirm_token 让前端弹窗确认。
     * 前端回传 confirmMessage："确定创建 2026-09-01 周年纪念日？"
     * 用户确认后调用 confirmCreateAnniversary(confirm_token) 真正写入。
     */
    @Tool("创建纪念日（第一步：返回确认 token，不直接写入）。需要用户在前端二次确认。")
    public Map<String, Object> prepareCreateAnniversary(
            @P("纪念日名称，例如：周年纪念") String name,
            @P("纪念日日期 yyyy-MM-dd") String date,
            @P("是否每年重复 true/false，默认 false") Boolean recurring,
            @P("提前几天提醒，默认 1") Integer remindDays,
            @P("备注描述，最多 200 字") String description) {

        Long userId = AiUserContext.requireUserId();
        log.info("[AI-TOOL] prepareCreateAnniversary userId={}, name='{}', date='{}'",
                userId, name, date);

        if (name == null || name.isBlank()) return Map.of("error", "纪念日名称不能为空");
        LocalDate parsedDate;
        try {
            parsedDate = LocalDate.parse(date);
        } catch (Exception e) {
            return Map.of("error", "日期格式错误，应为 yyyy-MM-dd：" + date);
        }
        boolean isRecurring = Boolean.TRUE.equals(recurring);
        int remind = (remindDays == null || remindDays < 0) ? 1 : Math.min(remindDays, 30);

        // 生成 token 暂存待创建 DTO
        String token = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        pendingMap.put(token, new PendingCreate(
                userId.intValue(),
                name.trim(),
                parsedDate,
                isRecurring,
                remind,
                description == null ? "" : description.substring(0, Math.min(description.length(), 200))
        ));
        // 1 小时过期
        new java.util.Timer().schedule(new java.util.TimerTask() {
            @Override public void run() { pendingMap.remove(token); }
        }, 60 * 60 * 1000L);

        return Map.of(
                "status", "CONFIRM_REQUIRED",
                "confirm_token", token,
                "preview", Map.of(
                        "name", name,
                        "date", parsedDate.toString(),
                        "recurring", isRecurring,
                        "remindDays", remind,
                        "description", description == null ? "" : description),
                "hint", "请向用户复述以上内容并请求确认；用户确认后再调用 confirmCreateAnniversary(token='"
                        + token + "')。"
        );
    }

    /**
     * 创建纪念日（**二次确认** 第二步）
     * <p>
     * 仅在用户在前端确认后才被 LLM 调用。校验 token → 真正写入 DB。
     */
    @Tool("创建纪念日（第二步：用户已确认后真正写入）。必须传入 prepareCreateAnniversary 返回的 confirm_token。")
    public Map<String, Object> confirmCreateAnniversary(
            @P("prepareCreateAnniversary 返回的确认 token") String confirmToken) {

        Long userId = AiUserContext.requireUserId();
        log.info("[AI-TOOL] confirmCreateAnniversary userId={}, token={}", userId, confirmToken);
        if (confirmToken == null || confirmToken.isBlank()) {
            return Map.of("error", "confirm_token 不能为空");
        }
        PendingCreate pending = pendingMap.remove(confirmToken);
        if (pending == null) {
            return Map.of("error", "确认凭证无效或已过期，请重新发起创建");
        }
        if (!pending.userId.equals(userId.intValue())) {
            return Map.of("error", "确认凭证归属错误");
        }
        AnniversaryDTO dto = new AnniversaryDTO();
        dto.setName(pending.name);
        dto.setAnniversaryDate(pending.date);
        dto.setIsRecurring(pending.recurring);
        dto.setRemindDays(pending.remindDays);
        dto.setDescription(pending.description);
        try {
            Result<AnniversaryVO> result = anniversaryService.createAnniversary(
                    pending.userId, dto);
            if (result == null || !result.isSuccess() || result.getData() == null) {
                return Map.of("error", result == null ? "null" : result.getMessage());
            }
            AnniversaryVO vo = result.getData();
            vo.calculateDaysUntil();
            return Map.of(
                    "status", "CREATED",
                    "anniversary", toMap(vo)
            );
        } catch (Exception e) {
            log.error("[AI-TOOL] confirmCreateAnniversary 失败", e);
            return Map.of("error", "创建失败：" + e.getMessage());
        }
    }

    // ==================== 内部 ====================

    private Map<String, Object> toMap(AnniversaryVO vo) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", vo.getId());
        map.put("name", vo.getName());
        map.put("anniversaryDate", vo.getAnniversaryDate() != null ? vo.getAnniversaryDate().toString() : null);
        map.put("recurring", vo.getIsRecurring());
        map.put("remindDays", vo.getRemindDays());
        map.put("description", vo.getDescription());
        map.put("daysUntil", vo.getDaysUntil());
        return map;
    }

    /** 待创建的纪念日 DTO（含过期机制） */
    private record PendingCreate(
            Integer userId,
            String name,
            LocalDate date,
            boolean recurring,
            int remindDays,
            String description
    ) {}
}