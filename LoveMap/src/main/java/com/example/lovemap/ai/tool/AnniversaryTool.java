package com.example.lovemap.ai.tool;

import com.example.lovemap.ai.context.AiUserContext;
import com.example.lovemap.ai.service.AiRecommendService;
import com.example.lovemap.common.Result;
import com.example.lovemap.common.constant.AiConstant;
import com.example.lovemap.model.dto.AnniversaryDTO;
import com.example.lovemap.model.dto.AiCopyFeedbackDTO;
import com.example.lovemap.model.vo.AnniversaryVO;
import com.example.lovemap.service.AiCopyFeedbackService;
import com.example.lovemap.service.AnniversaryService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 纪念日 AI 工具
 * <p>
 * 采用"逐步引导 + 二次确认"三阶段流程：
 * <ol>
 *   <li>collectAnniversaryField  ：每收到一个字段就调一次，把字段值写入草稿</li>
 *   <li>checkAnniversaryDraft    ：查询当前草稿，看还缺哪些必填字段</li>
 *   <li>prepareCreateAnniversary ：草稿齐全后生成确认 token，进入二次确认</li>
 *   <li>confirmCreateAnniversary ：用户确认后真正写入 DB</li>
 * </ol>
 * AI 必须按"收集 → 检查 → 准备 → 确认"顺序调用，逐步引导用户补齐字段。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnniversaryTool {

    private final AnniversaryService anniversaryService;
    private final AiRecommendService aiRecommendService;
    private final AiCopyFeedbackService aiCopyFeedbackService;

    /** 用户当前会话的纪念日草稿：userId -> 草稿 Map。仅在单次会话内有效。 */
    private final Map<Integer, Draft> draftMap = new ConcurrentHashMap<>();

    /** 二次确认 token -> 待操作的纪念日 DTO（PendingCreate/PendingUpdate/PendingDelete 共用）。仅在单次会话内有效。 */
    private final Map<String, Object> pendingMap = new ConcurrentHashMap<>();

    // ==================== 读取类 ====================

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

    // ==================== 文案生成 ====================

    /**
     * 为某个纪念日生成文案。可选传入风格、字数、用途等偏好。
     * <p>
     * 自动组合纪念日名称、原日期、剩余天数、在一起天数等上下文，再交由 LLM 生成 3-5 条文案。
     */
    @Tool("为某个纪念日生成 3-5 条中文文案（朋友圈/卡片/私信风格）。"
            + "传 anniversaryName 指定纪念日；style 可选 romantic/casual/humor/poetic，默认 romantic；"
            + "extra 可选，例如「50 字以内/适合发在卡片上/想突出陪伴」。")
    public Map<String, Object> generateAnniversaryCopy(
            @P("纪念日名称（模糊匹配）") String anniversaryName,
            @P("文案风格：romantic/casual/humor/poetic，默认 romantic") String style,
            @P("补充偏好（字数/用途/突出点），可空") String extra) {

        Long userId = AiUserContext.requireUserId();
        log.info("[AI-TOOL] generateAnniversaryCopy userId={}, name='{}', style='{}', extra='{}'",
                userId, anniversaryName, style, extra);
        try {
            AnniversaryVO vo = findAnniversaryByName(userId.intValue(), anniversaryName);
            if (vo == null) {
                return Map.of("error", "未找到纪念日：" + anniversaryName);
            }
            vo.calculateDaysUntil();

            StringBuilder hint = new StringBuilder();
            hint.append("纪念日名称：").append(vo.getName()).append('\n');
            if (vo.getAnniversaryDate() != null) {
                hint.append("纪念日日期：").append(vo.getAnniversaryDate()).append('\n');
            }
            if (vo.getDaysUntil() != null) {
                long days = vo.getDaysUntil();
                if (days == 0) {
                    hint.append("状态：就是今天\n");
                } else if (days > 0) {
                    hint.append("距离纪念日还有 ").append(days).append(" 天\n");
                } else {
                    hint.append("纪念日已过去 ").append(Math.abs(days)).append(" 天\n");
                }
            }
            if (vo.getDescription() != null && !vo.getDescription().isBlank()) {
                hint.append("备注：").append(vo.getDescription()).append('\n');
            }
            String styleNorm = (style == null || style.isBlank()) ? "romantic" : style.trim().toLowerCase();
            hint.append("风格：").append(switch (styleNorm) {
                case "casual" -> "casual（日常/轻松）";
                case "humor" -> "humor（幽默/搞怪）";
                case "poetic" -> "poetic（诗意/文艺）";
                default -> "romantic（浪漫/温柔）";
            }).append('\n');
            if (extra != null && !extra.isBlank()) {
                hint.append("补充要求：").append(extra.trim()).append('\n');
            }

            // 缓存 key 后缀 = anniversaryId : style : sha256(extra)[:10]
            // 这样同一纪念日 + 同一风格 + 同一补充要求,1 小时内复用 Redis 命中
            String cacheKeySuffix = vo.getId() + ":" + styleNorm + ":"
                    + com.example.lovemap.ai.service.AiRecommendService.sha256(extra).substring(0, 10);
            String copy = aiRecommendService.generateAnniversaryCopy(hint.toString(), cacheKeySuffix);

            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("status", "OK");
            resp.put("anniversary", Map.of(
                    "id", vo.getId(),
                    "name", vo.getName(),
                    "anniversaryDate", vo.getAnniversaryDate() == null ? null : vo.getAnniversaryDate().toString(),
                    "daysUntil", vo.getDaysUntil()));
            resp.put("style", styleNorm);
            resp.put("cacheKeySuffix", cacheKeySuffix);
            resp.put("copy", copy);
            return resp;
        } catch (Exception e) {
            log.error("[AI-TOOL] generateAnniversaryCopy 失败", e);
            return Map.of("error", "生成文案失败：" + e.getMessage());
        }
    }

    /**
     * 为「未来 30 天内即将到来的纪念日」批量生成文案预览。
     * <p>
     * 一次返回所有即将到来的纪念日及其对应文案，便于用户挑选。
     */
    @Tool("批量生成未来 30 天内即将到来的纪念日文案预览。每个纪念日一组 3-5 条文案。")
    public List<Map<String, Object>> generateUpcomingAnniversaryCopies(
            @P("文案风格 romantic/casual/humor/poetic，默认 romantic") String style) {

        Long userId = AiUserContext.requireUserId();
        log.info("[AI-TOOL] generateUpcomingAnniversaryCopies userId={}, style='{}'", userId, style);
        try {
            Result<List<AnniversaryVO>> r = anniversaryService.listAnniversaries(userId.intValue());
            if (r == null || r.getData() == null || r.getData().isEmpty()) {
                return List.of(Map.of("error", "当前用户暂无纪念日"));
            }
            LocalDate today = LocalDate.now();
            String styleNorm = (style == null || style.isBlank()) ? "romantic" : style.trim().toLowerCase();

            List<Map<String, Object>> out = new ArrayList<>();
            // 按 daysUntil 升序
            List<AnniversaryVO> sorted = new ArrayList<>(r.getData());
            for (AnniversaryVO vo : sorted) vo.calculateDaysUntil();
            sorted.sort((a, b) -> Long.compare(
                    a.getDaysUntil() == null ? Long.MAX_VALUE : a.getDaysUntil(),
                    b.getDaysUntil() == null ? Long.MAX_VALUE : b.getDaysUntil()));
            for (AnniversaryVO vo : sorted) {
                if (vo.getDaysUntil() == null || vo.getDaysUntil() < 0 || vo.getDaysUntil() > 30) continue;
                StringBuilder hint = new StringBuilder();
                hint.append("纪念日名称：").append(vo.getName()).append('\n');
                if (vo.getAnniversaryDate() != null) {
                    hint.append("纪念日日期：").append(vo.getAnniversaryDate()).append('\n');
                }
                hint.append("距离纪念日还有 ").append(vo.getDaysUntil()).append(" 天\n");
                if (vo.getDescription() != null && !vo.getDescription().isBlank()) {
                    hint.append("备注：").append(vo.getDescription()).append('\n');
                }
                hint.append("风格：").append(styleNorm);
                // 缓存后缀：批量场景下 extra 视为空字符串（用户未指定额外偏好）
                String cacheKeySuffix = vo.getId() + ":" + styleNorm + ":"
                        + com.example.lovemap.ai.service.AiRecommendService.sha256("").substring(0, 10);
                String copy = aiRecommendService.generateAnniversaryCopy(hint.toString(), cacheKeySuffix);

                Map<String, Object> item = new LinkedHashMap<>();
                item.put("anniversaryId", vo.getId());
                item.put("name", vo.getName());
                item.put("anniversaryDate", vo.getAnniversaryDate() == null ? null : vo.getAnniversaryDate().toString());
                item.put("daysUntil", vo.getDaysUntil());
                item.put("style", styleNorm);
                item.put("cacheKeySuffix", cacheKeySuffix);
                item.put("copy", copy);
                out.add(item);
            }
            if (out.isEmpty()) {
                return List.of(Map.of(
                        "status", "EMPTY",
                        "hint", "未来 30 天内没有即将到来的纪念日"));
            }
            return out;
        } catch (Exception e) {
            log.error("[AI-TOOL] generateUpcomingAnniversaryCopies 失败", e);
            return List.of(Map.of("error", "批量生成失败：" + e.getMessage()));
        }
    }

    /**
     * 提交文案采纳 / 拒绝反馈。
     * <p>
     * 调用时机：用户明确说「就这条 / 太烂了 / 不喜欢」时由 AI 调用，
     * 也可由前端在用户点击采纳按钮时直接走 REST 接口触发。
     */
    @Tool("提交 AI 纪念日文案的采纳/拒绝反馈。"
            + "cacheKeySuffix 是上一次 generateAnniversaryCopy 返回的同名字段；"
            + "feedback:1-采纳 2-拒绝；copyPreview 可传被采纳/拒绝的文案首条（截断到 200 字以内）。")
    public Map<String, Object> submitAnniversaryCopyFeedback(
            @P("缓存 key 后缀（generateAnniversaryCopy 返回的 cacheKeySuffix）") String cacheKeySuffix,
            @P("反馈类型：1-采纳 2-拒绝") Integer feedback,
            @P("采纳/拒绝时的文案首条摘要，可空") String copyPreview,
            @P("关联纪念日 ID，可空") Long anniversaryId,
            @P("文案风格 romantic/casual/humor/poetic") String style) {

        Long userId = AiUserContext.requireUserId();
        log.info("[AI-TOOL] submitAnniversaryCopyFeedback userId={}, cacheKeySuffix='{}', feedback={}",
                userId, cacheKeySuffix, feedback);

        if (cacheKeySuffix == null || cacheKeySuffix.isBlank()) {
            return Map.of("error", "cacheKeySuffix 不能为空");
        }
        if (feedback == null
                || (feedback != AiConstant.FEEDBACK_ACCEPTED && feedback != AiConstant.FEEDBACK_REJECTED)) {
            return Map.of("error", "feedback 必须为 1(采纳) 或 2(拒绝)");
        }
        String styleNorm = (style == null || style.isBlank()) ? "romantic" : style.trim().toLowerCase();
        String preview = copyPreview == null ? null
                : (copyPreview.length() > 200 ? copyPreview.substring(0, 200) : copyPreview);

        AiCopyFeedbackDTO dto = new AiCopyFeedbackDTO();
        dto.setCacheKeySuffix(cacheKeySuffix);
        dto.setFeedback(feedback);
        dto.setCopyPreview(preview);
        dto.setAnniversaryId(anniversaryId);
        dto.setStyle(styleNorm);

        try {
            Result<Void> r = aiCopyFeedbackService.submitFeedback(userId.intValue(), dto);
            if (r == null || !r.isSuccess()) {
                return Map.of("error", r == null ? "提交失败" : r.getMessage());
            }
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("status", "RECORDED");
            resp.put("feedback", feedback == AiConstant.FEEDBACK_ACCEPTED ? "accepted" : "rejected");
            resp.put("cacheKeySuffix", cacheKeySuffix);
            resp.put("hint", feedback == AiConstant.FEEDBACK_ACCEPTED
                    ? "已记录采纳,这条文案会被标记为高质量样本,后续 Prompt 优化时优先参考。"
                    : "已记录拒绝,后续会基于拒绝记录优化 Prompt,减少类似文案出现。");
            return resp;
        } catch (Exception e) {
            log.error("[AI-TOOL] submitAnniversaryCopyFeedback 失败", e);
            return Map.of("error", "提交反馈失败：" + e.getMessage());
        }
    }

    // ==================== 引导式创建（逐步收集字段） ====================

    /**
     * 必填字段定义：name / date / recurring / remindDays
     */
    private static final List<String> REQUIRED_FIELDS = List.of("name", "date", "recurring", "remindDays");

    /** 字段中文名，给 AI 用来组织引导话术（必须包含 description，否则 hint 会显示 null） */
    private static final Map<String, String> FIELD_LABEL = new java.util.LinkedHashMap<>() {{
        put("name", "纪念日名称");
        put("date", "纪念日日期(格式 yyyy-MM-dd)");
        put("recurring", "是否每年重复提醒(true/false)");
        put("remindDays", "提前多少天提醒(正整数，建议 1~30)");
        put("description", "备注描述(可选，最多 200 字)");
    }};

    /**
     * 第一步：收集单个字段值到草稿
     * <p>
     * AI 每拿到用户回答的一个字段，就调一次本方法。
     * field 取值：name / date / recurring / remindDays / description(可选)。
     */
    @Tool("逐步收集创建纪念日所需的字段。每收到用户回答的一个字段就调用一次：field 取值 name/date/recurring/remindDays/description。")
    public Map<String, Object> collectAnniversaryField(
            @P("字段名：name-纪念日名称，date-日期yyyy-MM-dd，recurring-是否每年重复，remindDays-提前几天提醒，description-备注") String field,
            @P("字段值。recurring 传 true/false，remindDays 传正整数，其它字段传字符串") String value) {

        Integer userId = AiUserContext.requireUserId().intValue();
        log.info("[AI-TOOL] collectAnniversaryField userId={}, field={}, value={}", userId, field, value);

        if (field == null || field.isBlank()) return Map.of("error", "field 不能为空");
        Draft draft = draftMap.computeIfAbsent(userId, k -> new Draft());
        String key = field.trim();

        try {
            switch (key) {
                case "name" -> {
                    String v = value == null ? "" : value.trim();
                    if (v.isEmpty()) return Map.of("error", "纪念日名称不能为空");
                    draft.name = v;
                }
                case "date" -> {
                    if (value == null || value.isBlank()) return Map.of("error", "日期不能为空");
                    try {
                        draft.date = LocalDate.parse(value.trim());
                    } catch (DateTimeParseException e) {
                        return Map.of("error", "日期格式错误，应为 yyyy-MM-dd，例如 2026-09-01");
                    }
                }
                case "recurring" -> {
                    if (value == null) return Map.of("error", "recurring 不能为空，传 true 或 false");
                    String v = value.trim().toLowerCase();
                    if ("true".equals(v) || "1".equals(v) || "yes".equals(v) || "是".equals(v)) {
                        draft.recurring = true;
                    } else if ("false".equals(v) || "0".equals(v) || "no".equals(v) || "否".equals(v)) {
                        draft.recurring = false;
                    } else {
                        return Map.of("error", "recurring 只能传 true 或 false");
                    }
                }
                case "remindDays" -> {
                    if (value == null || value.isBlank()) return Map.of("error", "remindDays 不能为空");
                    int n;
                    try {
                        n = Integer.parseInt(value.trim());
                    } catch (NumberFormatException e) {
                        return Map.of("error", "remindDays 必须是正整数，例如 1/3/7");
                    }
                    if (n < 1 || n > 60) return Map.of("error", "remindDays 范围应为 1~60");
                    draft.remindDays = n;
                }
                case "description" -> {
                    String v = value == null ? "" : value.trim();
                    if (v.length() > 200) v = v.substring(0, 200);
                    draft.description = v;
                }
                default -> {
                    return Map.of("error", "未知字段：" + field + "，仅支持 name/date/recurring/remindDays/description");
                }
            }
        } catch (Exception e) {
            log.error("[AI-TOOL] collectAnniversaryField 失败", e);
            return Map.of("error", "字段保存失败：" + e.getMessage());
        }

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("status", "COLLECTED");
        resp.put("field", key);
        resp.put("currentDraft", draftView(draft));
        resp.put("hint", "已记录字段「" + FIELD_LABEL.getOrDefault(key, key) + "」。" + nextFieldHint(draft));
        return resp;
    }

    /**
     * 第二步：检查草稿，返回还缺哪些必填字段
     */
    @Tool("检查当前纪念日创建草稿。返回已收集字段、缺失字段列表、以及下一步动作建议。")
    public Map<String, Object> checkAnniversaryDraft() {
        Integer userId = AiUserContext.requireUserId().intValue();
        log.info("[AI-TOOL] checkAnniversaryDraft userId={}", userId);

        Draft draft = draftMap.get(userId);
        if (draft == null) {
            return Map.of(
                    "status", "EMPTY",
                    "missing_fields", REQUIRED_FIELDS,
                    "hint", "尚未开始收集，请先调用 collectAnniversaryField 向用户询问「纪念日名称」"
            );
        }

        List<String> missing = new ArrayList<>();
        if (draft.name == null) missing.add("name");
        if (draft.date == null) missing.add("date");
        if (draft.recurring == null) missing.add("recurring");
        if (draft.remindDays == null) missing.add("remindDays");

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("status", missing.isEmpty() ? "READY" : "INCOMPLETE");
        resp.put("currentDraft", draftView(draft));
        resp.put("missing_fields", missing);
        resp.put("hint", missing.isEmpty()
                ? "草稿 status=READY，必填字段已齐全。【必须】下一步调用 prepareCreateAnniversary 生成确认 token 推送给用户二次确认；"
                  + "确认完成后立刻调用 confirmCreateAnniversary 写入数据库。**禁止在 prepare 之前结束对话或自行总结**。"
                : "草稿 status=INCOMPLETE，还缺少字段：" + missing + "。请按顺序逐个向用户询问并调用 collectAnniversaryField 收集。");
        return resp;
    }

    /**
     * 第三步：草稿齐全后生成确认 token（不写入 DB，等用户在前端确认）
     * <p>
     * 必须在 checkAnniversaryDraft 返回 status=READY 后才能调用。
     */
    @Tool("当所有必填字段已收集齐全时调用，生成二次确认 token 并返回给前端弹窗。注意：仅在 checkAnniversaryDraft 返回 status=READY 后才调用。")
    public Map<String, Object> prepareCreateAnniversary() {
        Long userIdLong = AiUserContext.requireUserId();
        Integer userId = userIdLong.intValue();
        log.info("[AI-TOOL] prepareCreateAnniversary userId={}", userId);

        Draft draft = draftMap.get(userId);
        if (draft == null) {
            return Map.of("error", "草稿不存在，请先调用 collectAnniversaryField 收集字段");
        }
        List<String> missing = new ArrayList<>();
        if (draft.name == null) missing.add("name");
        if (draft.date == null) missing.add("date");
        if (draft.recurring == null) missing.add("recurring");
        if (draft.remindDays == null) missing.add("remindDays");
        if (!missing.isEmpty()) {
            return Map.of(
                    "error", "字段未齐全，缺少：" + missing,
                    "missing_fields", missing,
                    "hint", "请先逐个向用户询问缺失字段并调用 collectAnniversaryField 收集"
            );
        }

        String token = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        pendingMap.put(token, new PendingCreate(
                userId,
                draft.name,
                draft.date,
                draft.recurring,
                draft.remindDays,
                draft.description == null ? "" : draft.description
        ));
        new java.util.Timer().schedule(new java.util.TimerTask() {
            @Override public void run() { pendingMap.remove(token); }
        }, 60 * 60 * 1000L);

        // 清理草稿（生成 token 后无需再保留）
        draftMap.remove(userId);

        return Map.of(
                "status", "CONFIRM_REQUIRED",
                "confirm_token", token,
                "preview", Map.of(
                        "name", draft.name,
                        "date", draft.date.toString(),
                        "recurring", draft.recurring,
                        "remindDays", draft.remindDays,
                        "description", draft.description == null ? "" : draft.description),
                "hint", "请向用户复述以上预览内容并请求确认；用户在前端确认后调用 confirmCreateAnniversary(token='"
                        + token + "') 真正写入。"
        );
    }

    /**
     * 第四步：用户在前端确认后真正写入 DB
     */
    @Tool("创建纪念日最后一步：用户已确认后真正写入。必须传入 prepareCreateAnniversary 返回的 confirm_token。")
    public Map<String, Object> confirmCreateAnniversary(
            @P("prepareCreateAnniversary 返回的确认 token") String confirmToken) {

        Long userId = AiUserContext.requireUserId();
        log.info("[AI-TOOL] confirmCreateAnniversary userId={}, token={}", userId, confirmToken);
        if (confirmToken == null || confirmToken.isBlank()) {
            return Map.of("error", "confirm_token 不能为空");
        }
        PendingCreate pending = (PendingCreate) pendingMap.remove(confirmToken);
        if (pending == null) {
            return Map.of("error", "确认凭证无效或已过期，请重新调用 prepareCreateAnniversary");
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

    // ==================== 写操作：直接修改/删除（二次确认） ====================
    //
    // 注意：与上面"create 流程"的区别
    //   - create 走"字段收集 + 确认"，因为新纪念日用户不一定有完整字段
    //   - update/delete 用户已指定名称/ID，AI 直接读现状 → 用户确认 → 写入，无需分步收集

    /**
     * 修改纪念日（第一步：返回确认 token）。支持按名称模糊匹配；name/date/recurring/remindDays/description 任选传。
     * <p>
     * 仅传入的字段会被覆盖，未传字段保持原值。
     */
    @Tool("修改一个纪念日（第一步：返回确认 token，不直接写入）。按名称模糊匹配；name/date/recurring/remindDays/description 任选传，未传字段保持原值。需要用户在前端二次确认。")
    public Map<String, Object> prepareUpdateAnniversary(
            @P("纪念日名称（模糊匹配现有纪念日）") String name,
            @P("新名称，传 null 表示不改") String newName,
            @P("新日期 yyyy-MM-dd，传 null 表示不改") String newDate,
            @P("是否每年重复，传 null 表示不改") Boolean recurring,
            @P("提前几天提醒（正整数 1-60），传 null 表示不改") Integer remindDays,
            @P("新备注，传 null 表示不改") String description) {

        Long userId = AiUserContext.requireUserId();
        log.info("[AI-TOOL] prepareUpdateAnniversary userId={}, name='{}', newName='{}', newDate='{}', recurring={}, remindDays={}",
                userId, name, newName, newDate, recurring, remindDays);

        // 1. 定位纪念日
        AnniversaryVO target = findAnniversaryByName(userId.intValue(), name);
        if (target == null) return Map.of("error", "未找到纪念日：" + name);

        // 2. 至少传一个可改字段
        if (newName == null && newDate == null && recurring == null && remindDays == null && description == null) {
            return Map.of("error", "至少需要传入一个要修改的字段（newName / newDate / recurring / remindDays / description）");
        }

        // 3. 字段校验
        LocalDate parsedDate = null;
        if (newDate != null) {
            try {
                parsedDate = LocalDate.parse(newDate.trim());
            } catch (DateTimeParseException e) {
                return Map.of("error", "日期格式错误，应为 yyyy-MM-dd：" + newDate);
            }
        }
        if (remindDays != null && (remindDays < 1 || remindDays > 60)) {
            return Map.of("error", "remindDays 范围应为 1~60");
        }
        String trimmedNewName = null;
        if (newName != null) {
            trimmedNewName = newName.trim();
            if (trimmedNewName.isEmpty()) return Map.of("error", "新名称不能为空");
        }

        // 4. 暂存 + 预览
        String token = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        pendingMap.put(token, new PendingUpdate(
                userId.intValue(),
                target.getId(),
                trimmedNewName,
                parsedDate,
                recurring,
                remindDays,
                description == null ? null : description.substring(0, Math.min(description.length(), 200)),
                System.currentTimeMillis()
        ));
        new java.util.Timer().schedule(new java.util.TimerTask() {
            @Override public void run() { pendingMap.remove(token); }
        }, 60 * 60 * 1000L);

        Map<String, Object> preview = new LinkedHashMap<>();
        preview.put("anniversaryId", target.getId());
        preview.put("name", target.getName());
        preview.put("oldName", target.getName());
        preview.put("oldDate", target.getAnniversaryDate() == null ? null : target.getAnniversaryDate().toString());
        preview.put("oldRecurring", target.getIsRecurring());
        preview.put("oldRemindDays", target.getRemindDays());
        preview.put("oldDescription", target.getDescription());
        if (trimmedNewName != null) preview.put("newName", trimmedNewName);
        if (parsedDate != null) preview.put("newDate", parsedDate.toString());
        if (recurring != null) preview.put("newRecurring", recurring);
        if (remindDays != null) preview.put("newRemindDays", remindDays);
        if (description != null) preview.put("newDescription", description);

        return Map.of(
                "status", "CONFIRM_REQUIRED",
                "confirm_token", token,
                "preview", preview,
                "hint", "⚠️ 尚未修改！请向用户复述以上原值→新值并明确请求用户确认。"
                        + "用户明确说'确认/同意'后调用 confirmUpdateAnniversary(token=\"" + token + "\")。"
                        + "在用户确认前禁止告诉用户'已修改'。"
        );
    }

    /**
     * 修改纪念日（第二步：用户已确认后真正写入）
     */
    @Tool("修改纪念日第二步：用户已确认后真正写入。必须传入 prepareUpdateAnniversary 返回的 confirm_token。")
    public Map<String, Object> confirmUpdateAnniversary(
            @P("prepareUpdateAnniversary 返回的确认 token") String confirmToken) {

        Long userId = AiUserContext.requireUserId();
        log.info("[AI-TOOL] confirmUpdateAnniversary userId={}, token={}", userId, confirmToken);
        if (confirmToken == null || confirmToken.isBlank()) return Map.of("error", "confirm_token 不能为空");

        PendingUpdate pending = (PendingUpdate) pendingMap.remove(confirmToken);
        if (pending == null) return Map.of("error", "确认凭证无效或已过期，请重新发起修改");
        if (!pending.userId.equals(userId.intValue())) return Map.of("error", "确认凭证归属错误");
        if (System.currentTimeMillis() - pending.createdAtMs > 10 * 60 * 1000L) {
            return Map.of("error", "确认凭证已过期，请重新发起修改");
        }

        AnniversaryDTO dto = new AnniversaryDTO();
        dto.setName(pending.newName);
        dto.setAnniversaryDate(pending.newDate);
        dto.setIsRecurring(pending.recurring);
        dto.setRemindDays(pending.remindDays);
        dto.setDescription(pending.description);

        try {
            Result<AnniversaryVO> r = anniversaryService.updateAnniversary(pending.userId, pending.anniversaryId, dto);
            if (r == null || !r.isSuccess() || r.getData() == null) {
                return Map.of("error", r == null ? "null" : r.getMessage());
            }
            AnniversaryVO vo = r.getData();
            vo.calculateDaysUntil();
            Map<String, Object> resp = toMap(vo);
            resp.put("status", "UPDATED");
            resp.put("action", "update");
            return resp;
        } catch (Exception e) {
            log.error("[AI-TOOL] confirmUpdateAnniversary 失败", e);
            return Map.of("error", "修改失败：" + e.getMessage());
        }
    }

    /**
     * 删除纪念日（第一步：返回确认 token）。按名称模糊匹配。
     */
    @Tool("删除一个纪念日（第一步：返回确认 token，不直接写入）。按名称模糊匹配；不可逆，需要用户在前端二次确认。")
    public Map<String, Object> prepareDeleteAnniversary(
            @P("纪念日名称（模糊匹配现有纪念日）") String name) {

        Long userId = AiUserContext.requireUserId();
        log.info("[AI-TOOL] prepareDeleteAnniversary userId={}, name='{}'", userId, name);
        if (name == null || name.isBlank()) return Map.of("error", "纪念日名称不能为空");

        AnniversaryVO target = findAnniversaryByName(userId.intValue(), name);
        if (target == null) return Map.of("error", "未找到纪念日：" + name);

        String token = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        pendingMap.put(token, new PendingDelete(
                userId.intValue(),
                target.getId(),
                target.getName(),
                System.currentTimeMillis()
        ));
        new java.util.Timer().schedule(new java.util.TimerTask() {
            @Override public void run() { pendingMap.remove(token); }
        }, 60 * 60 * 1000L);

        Map<String, Object> preview = new LinkedHashMap<>();
        preview.put("action", "delete");
        preview.put("anniversaryId", target.getId());
        preview.put("name", target.getName());
        preview.put("anniversaryDate", target.getAnniversaryDate() == null ? null : target.getAnniversaryDate().toString());
        preview.put("warning", "纪念日和关联的提醒计划会被一并删除");

        return Map.of(
                "status", "CONFIRM_REQUIRED",
                "confirm_token", token,
                "preview", preview,
                "hint", "⚠️ 尚未删除！这是一个不可逆操作。请向用户复述以上纪念日信息并明确请求用户确认。"
                        + "用户明确说'确认/同意/删吧'后调用 confirmDeleteAnniversary(token=\"" + token + "\")。"
                        + "在用户确认前禁止告诉用户'已删除'。"
        );
    }

    /**
     * 删除纪念日（第二步：用户已确认后真正写入）
     */
    @Tool("删除纪念日第二步：用户已确认后真正写入。必须传入 prepareDeleteAnniversary 返回的 confirm_token。")
    public Map<String, Object> confirmDeleteAnniversary(
            @P("prepareDeleteAnniversary 返回的确认 token") String confirmToken) {

        Long userId = AiUserContext.requireUserId();
        log.info("[AI-TOOL] confirmDeleteAnniversary userId={}, token={}", userId, confirmToken);
        if (confirmToken == null || confirmToken.isBlank()) return Map.of("error", "confirm_token 不能为空");

        PendingDelete pending = (PendingDelete) pendingMap.remove(confirmToken);
        if (pending == null) return Map.of("error", "确认凭证无效或已过期，请重新发起删除");
        if (!pending.userId.equals(userId.intValue())) return Map.of("error", "确认凭证归属错误");
        if (System.currentTimeMillis() - pending.createdAtMs > 10 * 60 * 1000L) {
            return Map.of("error", "确认凭证已过期，请重新发起删除");
        }

        try {
            Result<Void> r = anniversaryService.deleteAnniversary(pending.userId, pending.anniversaryId);
            if (r == null || !r.isSuccess()) {
                return Map.of("error", r == null ? "null" : r.getMessage());
            }
            return Map.of(
                    "status", "DELETED",
                    "action", "delete",
                    "anniversaryId", pending.anniversaryId,
                    "name", pending.name
            );
        } catch (Exception e) {
            log.error("[AI-TOOL] confirmDeleteAnniversary 失败", e);
            return Map.of("error", "删除失败：" + e.getMessage());
        }
    }

    /**
     * 按名称模糊查找纪念日，找不到返回 null
     */
    private AnniversaryVO findAnniversaryByName(int userId, String name) {
        if (name == null || name.isBlank()) return null;
        Result<List<AnniversaryVO>> r = anniversaryService.listAnniversaries(userId);
        if (r == null || r.getData() == null || r.getData().isEmpty()) return null;
        String kw = name.trim().toLowerCase();
        AnniversaryVO best = null;
        for (AnniversaryVO vo : r.getData()) {
            if (vo.getName() == null) continue;
            String ln = vo.getName().toLowerCase();
            if (ln.equals(kw)) return vo;
            if (best == null && ln.contains(kw)) best = vo;
        }
        return best;
    }

    // ==================== 内部 ====================

    private Map<String, Object> toMap(AnniversaryVO vo) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", vo.getId());
        map.put("name", vo.getName());
        map.put("anniversaryDate", vo.getAnniversaryDate() != null ? vo.getAnniversaryDate().toString() : null);
        map.put("recurring", vo.getIsRecurring());
        map.put("remindDays", vo.getRemindDays());
        map.put("description", vo.getDescription());
        map.put("daysUntil", vo.getDaysUntil());
        return map;
    }

    private Map<String, Object> draftView(Draft draft) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", draft.name);
        map.put("date", draft.date == null ? null : draft.date.toString());
        map.put("recurring", draft.recurring);
        map.put("remindDays", draft.remindDays);
        map.put("description", draft.description);
        return map;
    }

    /** 收集完一个字段后，提示下一步该问哪个字段；必填全收齐后强制 LLM 继续调 checkAnniversaryDraft */
    private String nextFieldHint(Draft draft) {
        if (draft.name == null) return "下一步向用户询问「" + FIELD_LABEL.get("name") + "」。";
        if (draft.date == null) return "下一步向用户询问「" + FIELD_LABEL.get("date") + "」。";
        if (draft.recurring == null) return "下一步向用户询问「" + FIELD_LABEL.get("recurring") + "」。";
        if (draft.remindDays == null) return "下一步向用户询问「" + FIELD_LABEL.get("remindDays") + "」。";
        if (draft.description == null) {
            return "必填字段已收集完毕。可以向用户询问「" + FIELD_LABEL.get("description")
                    + "」作为补充（用户说「不用/跳过」则不传 description）；"
                    + "询问后必须继续调用 checkAnniversaryDraft 校验草稿,不要直接结束对话。";
        }
        // description 已收集,必填+可选都齐全 → 强制 LLM 进入下一步
        return "全部字段已收集完毕。下一步【必须】调用 checkAnniversaryDraft 校验草稿，"
                + "得到 status=READY 后立刻调用 prepareCreateAnniversary 生成确认 token，"
                + "再让用户在前端确认。**禁止在此步骤结束对话或自行总结**。";
    }

    /** 草稿：单用户会话内有效 */
    private static class Draft {
        String name;
        LocalDate date;
        Boolean recurring;
        Integer remindDays;
        String description;
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

    /** 待修改的纪念日（含过期机制） */
    private record PendingUpdate(
            Integer userId,
            Long anniversaryId,
            String newName,
            LocalDate newDate,
            Boolean recurring,
            Integer remindDays,
            String description,
            long createdAtMs
    ) {}

    /** 待删除的纪念日（含过期机制） */
    private record PendingDelete(
            Integer userId,
            Long anniversaryId,
            String name,
            long createdAtMs
    ) {}
}
