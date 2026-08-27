package com.example.lovemap.ai.tool;

import com.example.lovemap.ai.context.AiUserContext;
import com.example.lovemap.common.Result;
import com.example.lovemap.model.dto.ExportDTO;
import com.example.lovemap.model.vo.ExportRecordVO;
import com.example.lovemap.service.ExportService;
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
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 导出 AI 工具
 * <p>
 * - listExportHistory       : 读取历史导出记录
 * - getExportStatus         : 读取单个导出任务状态
 * - prepareCreateExport     : 创建导出任务（**二次确认** 第一步），仅生成 confirm_token
 * - confirmCreateExport     : 创建导出任务（**二次确认** 第二步），真正写入
 * <p>
 * 写操作严格二次确认：prepare 阶段只校验参数并暂存 DTO 到内存；用户在前端确认后，
 * confirm 阶段才真正调用 ExportService.createExport 异步生成导出文件。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExportTool {

    private final ExportService exportService;

    /** 待创建的导出任务：token -> PendingExport。仅在单次会话内有效，1 小时过期。 */
    private final Map<String, PendingExport> pendingMap = new ConcurrentHashMap<>();

    // ==================== 查询 ====================

    /**
     * 查询导出历史（最多返回最近 20 条）
     */
    @Tool("查询当前用户的导出任务历史（最近 20 条），包含状态、格式、照片数、文件大小等。")
    public List<Map<String, Object>> listExportHistory() {
        Long userId = AiUserContext.requireUserId();
        log.info("[AI-TOOL] listExportHistory userId={}", userId);
        try {
            Result<List<ExportRecordVO>> result = exportService.getExportHistory(userId.intValue());
            if (result == null || result.getData() == null) return List.of();
            List<Map<String, Object>> out = new ArrayList<>();
            int limit = Math.min(result.getData().size(), 20);
            for (int i = 0; i < limit; i++) {
                out.add(toMap(result.getData().get(i)));
            }
            return out;
        } catch (Exception e) {
            log.error("[AI-TOOL] listExportHistory 失败", e);
            return List.of(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 查询单个导出任务的状态
     */
    @Tool("根据导出任务 ID 查询单个任务的当前状态（pending/processing/completed/failed）。")
    public Map<String, Object> getExportStatus(@P("导出任务 ID") Long exportId) {
        Long userId = AiUserContext.requireUserId();
        log.info("[AI-TOOL] getExportStatus userId={}, exportId={}", userId, exportId);
        if (exportId == null) {
            return Map.of("error", "exportId 不能为空");
        }
        try {
            Result<ExportRecordVO> result = exportService.getExportStatus(userId.intValue(), exportId);
            if (result == null || result.getData() == null) {
                return Map.of("error", "导出任务不存在或无权访问");
            }
            return toMap(result.getData());
        } catch (Exception e) {
            log.error("[AI-TOOL] getExportStatus 失败", e);
            return Map.of("error", "查询失败：" + e.getMessage());
        }
    }

    // ==================== 创建（二次确认） ====================

    /**
     * 创建导出任务（**二次确认** 第一步）
     * <p>
     * 校验参数 → 生成 confirm_token → 暂存 ExportDTO → 返回预览让用户确认。
     */
    @Tool("创建照片导出任务（第一步：返回确认 token，不真正创建）。需要用户在前端二次确认。支持格式：zip/pdf；支持范围：全部、按相册、按日期范围。")
    public Map<String, Object> prepareCreateExport(
            @P("导出格式：zip 或 pdf，默认 zip") String format,
            @P("导出类型：all-全部 / album-按相册 / date-按日期范围；默认 all") String exportType,
            @P("相册 ID（exportType=album 时必填）") Long albumId,
            @P("起始日期 yyyy-MM-dd（exportType=date 时必填）") String startDate,
            @P("结束日期 yyyy-MM-dd（exportType=date 时必填）") String endDate) {

        Long userId = AiUserContext.requireUserId();
        log.info("[AI-TOOL] prepareCreateExport userId={}, format='{}', type='{}', albumId={}, range={}~{}",
                userId, format, exportType, albumId, startDate, endDate);

        // 1. 校验格式
        String fmt = (format == null || format.isBlank()) ? "zip" : format.trim().toLowerCase();
        if (!"zip".equals(fmt) && !"pdf".equals(fmt)) {
            return Map.of("error", "导出格式仅支持 zip 或 pdf");
        }

        // 2. 校验导出类型
        String type = (exportType == null || exportType.isBlank()) ? "all" : exportType.trim().toLowerCase();
        if (!List.of("all", "album", "date").contains(type)) {
            return Map.of("error", "导出类型仅支持 all / album / date");
        }

        LocalDate start = null, end = null;
        Long resolvedAlbumId = null;

        // 3. 按类型校验必填字段
        switch (type) {
            case "album" -> {
                if (albumId == null) {
                    return Map.of("error", "exportType=album 时必须传入 albumId");
                }
                resolvedAlbumId = albumId;
            }
            case "date" -> {
                if (startDate == null || startDate.isBlank() || endDate == null || endDate.isBlank()) {
                    return Map.of("error", "exportType=date 时必须传入 startDate 和 endDate");
                }
                try {
                    start = LocalDate.parse(startDate.trim());
                    end = LocalDate.parse(endDate.trim());
                } catch (Exception e) {
                    return Map.of("error", "日期格式错误，应为 yyyy-MM-dd：" + startDate + " ~ " + endDate);
                }
                if (end.isBefore(start)) {
                    return Map.of("error", "结束日期不能早于起始日期");
                }
            }
            default -> {
                // all：无需额外字段
            }
        }

        // 4. 组装 DTO
        ExportDTO dto = new ExportDTO();
        dto.setFormat(fmt);
        dto.setExportType(type);
        dto.setAlbumId(resolvedAlbumId);
        dto.setStartDate(start);
        dto.setEndDate(end);

        // 5. 生成 token 暂存
        String token = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        pendingMap.put(token, new PendingExport(
                userId.intValue(),
                dto,
                System.currentTimeMillis()
        ));
        // 1 小时清理窗口（与 UserProfileTool 一致）
        new Timer().schedule(new TimerTask() {
            @Override public void run() { pendingMap.remove(token); }
        }, 60 * 60 * 1000L);

        // 6. 构造预览
        Map<String, Object> preview = new HashMap<>();
        preview.put("format", fmt);
        preview.put("exportType", type);
        if (resolvedAlbumId != null) preview.put("albumId", resolvedAlbumId);
        if (start != null) preview.put("startDate", start.toString());
        if (end != null) preview.put("endDate", end.toString());

        Map<String, Object> resp = new HashMap<>();
        resp.put("status", "CONFIRM_REQUIRED");
        resp.put("confirm_token", token);
        resp.put("preview", preview);
        resp.put("hint", "⚠️ 导出任务尚未创建！请向用户复述以上导出选项（格式/范围/日期等）并明确请求用户确认。"
                + "用户明确说'确认/同意/好的'之后，你**必须**立即调用工具 confirmCreateExport(confirm_token=\""
                + token + "\") 真正创建导出任务。"
                + "在用户确认前**禁止**告诉用户'已创建/已开始/成功'。"
                + "如果用户没有明确确认或表达拒绝，**禁止**调用 confirm* 工具。");
        return resp;
    }

    /**
     * 创建导出任务（**二次确认** 第二步）
     * <p>
     * 校验 token → 校验 10 分钟有效期 → 调用 ExportService.createExport 真正异步执行。
     */
    @Tool("创建导出任务（第二步：用户已确认后真正创建）。必须传入 prepareCreateExport 返回的 confirm_token。")
    public Map<String, Object> confirmCreateExport(
            @P("prepareCreateExport 返回的确认 token") String confirmToken) {

        Long userId = AiUserContext.requireUserId();
        log.info("[AI-TOOL] confirmCreateExport userId={}, token={}", userId, confirmToken);

        if (confirmToken == null || confirmToken.isBlank()) {
            return Map.of("error", "confirm_token 不能为空");
        }
        PendingExport pending = pendingMap.remove(confirmToken);
        if (pending == null) {
            return Map.of("error", "确认凭证无效或已过期，请重新发起导出");
        }
        if (!pending.userId.equals(userId.intValue())) {
            return Map.of("error", "确认凭证归属错误");
        }
        // 10 分钟过期
        if (System.currentTimeMillis() - pending.createdAtMs > 10 * 60 * 1000L) {
            return Map.of("error", "确认凭证已过期，请重新发起导出");
        }

        try {
            Result<ExportRecordVO> result = exportService.createExport(
                    pending.userId, pending.dto);
            if (result == null || !result.isSuccess() || result.getData() == null) {
                log.warn("[AI-TOOL] confirmCreateExport 业务失败 userId={}, token={}, msg={}",
                        pending.userId, confirmToken,
                        result == null ? "null" : result.getMessage());
                return Map.of("error", result == null ? "null" : result.getMessage());
            }
            ExportRecordVO vo = result.getData();
            log.info("[AI-TOOL] confirmCreateExport 成功 userId={}, taskId={}, format={}, photoCount={}, status={}",
                    pending.userId, vo.getId(), vo.getFormat(), vo.getPhotoCount(), vo.getStatus());
            Map<String, Object> map = toMap(vo);
            map.put("status", "CREATED");
            return map;
        } catch (Exception e) {
            log.error("[AI-TOOL] confirmCreateExport 异常 userId={}, token={}", pending.userId, confirmToken, e);
            return Map.of("error", "创建导出任务失败：" + e.getMessage());
        }
    }

    // ==================== 内部 ====================

    private Map<String, Object> toMap(ExportRecordVO vo) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", vo.getId());
        map.put("format", vo.getFormat());
        map.put("startDate", vo.getStartDate());
        map.put("endDate", vo.getEndDate());
        map.put("photoCount", vo.getPhotoCount());
        map.put("status", vo.getStatus());
        map.put("fileSize", vo.getFileSize());
        map.put("createdAt", vo.getCreatedAt() != null ? vo.getCreatedAt().toString() : null);
        map.put("completedAt", vo.getCompletedAt() != null ? vo.getCompletedAt().toString() : null);
        return map;
    }

    /** 待创建的导出任务（含过期机制） */
    private record PendingExport(
            Integer userId,
            ExportDTO dto,
            long createdAtMs
    ) {}
}