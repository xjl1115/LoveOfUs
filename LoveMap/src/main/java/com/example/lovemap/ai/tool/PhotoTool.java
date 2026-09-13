package com.example.lovemap.ai.tool;

import com.example.lovemap.ai.context.AiUserContext;
import com.example.lovemap.common.Result;
import com.example.lovemap.model.vo.PhotoDetailVO;
import com.example.lovemap.model.vo.TimelineGroupVO;
import com.example.lovemap.model.vo.TimelinePhotoVO;
import com.example.lovemap.model.vo.TimelineResultVO;
import com.example.lovemap.service.PhotoService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 照片相关 AI 工具
 * <p>
 * 工具方法返回的 Map/字符串会原样回灌给 LLM，让 LLM 用自然语言总结给用户。
 * 所有查询都走 PhotoService，自动按 groupId 隔离。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PhotoTool {

    private final PhotoService photoService;

    /** 删除照片的二次确认 token 缓存 */
    private final Map<String, PendingDeletePhoto> pendingMap = new ConcurrentHashMap<>();

    /**
     * 搜索照片（关键词 / 日期范围 / 城市）
     * <p>
     * keyword 可匹配 description（用户描述）、city（城市）、locationName（地点）
     * 仅按当前用户所在情侣组范围查询。
     */
    @Tool("搜索照片。支持关键词（地点/描述/城市）和日期范围筛选，按拍摄日期倒序。")
    public List<Map<String, Object>> searchPhotos(
            @P("关键词，例如：杭州、海边、樱花；传空字符串表示不过滤") String keyword,
            @P("起始日期 yyyy-MM-dd；传 null 表示不限") String startDate,
            @P("结束日期 yyyy-MM-dd；传 null 表示不限") String endDate,
            @P("返回数量上限，1-50，默认 10") Integer limit) {

        Long userId = AiUserContext.requireUserId();
        int safeLimit = (limit == null || limit <= 0) ? 10 : Math.min(limit, 50);
        LocalDate start = parseDate(startDate);
        LocalDate end = parseDate(endDate);
        // keyword 用于描述文本侧筛选；目前 PhotoService.getTimeline 不支持关键词筛选，
        // 因此我们先按日期+城市范围查询，再用 keyword 在结果里做二次过滤。
        String kw = (keyword == null) ? "" : keyword.trim().toLowerCase();

        log.info("[AI-TOOL] searchPhotos userId={}, keyword='{}', range={}~{}, limit={}",
                userId, kw, start, end, safeLimit);

        try {
            Result<TimelineResultVO> result = photoService.getTimeline(
                    userId.intValue(), 1, safeLimit, null, null, start, end);
            if (result == null || result.getData() == null || result.getData().getRecords() == null) {
                return Collections.emptyList();
            }

            List<Map<String, Object>> out = new ArrayList<>();
            outer:
            for (TimelineGroupVO group : result.getData().getRecords()) {
                if (group.getPhotos() == null) continue;
                for (TimelinePhotoVO p : group.getPhotos()) {
                    if (!kw.isEmpty() && !matchesKeyword(p, kw)) {
                        continue;
                    }
                    out.add(toMap(p));
                    if (out.size() >= safeLimit) break outer;
                }
            }
            return out;
        } catch (Exception e) {
            log.error("[AI-TOOL] searchPhotos 失败", e);
            return Collections.emptyList();
        }
    }

    /**
     * 描述单张照片：返回照片的元数据与已有描述，供 LLM 进一步润色
     */
    @Tool("获取单张照片的元数据（拍摄日期、地点、用户描述）。不会上传图片本身给 AI。")
    public Map<String, Object> describePhoto(@P("照片 ID") Long photoId) {
        Long userId = AiUserContext.requireUserId();
        log.info("[AI-TOOL] describePhoto userId={}, photoId={}", userId, photoId);
        if (photoId == null) {
            return Map.of("error", "photoId 不能为空");
        }
        try {
            Result<PhotoDetailVO> result = photoService.getPhotoDetail(userId.intValue(), photoId);
            if (result == null || result.getData() == null) {
                return Map.of("error", "照片不存在或已删除");
            }
            PhotoDetailVO p = result.getData();
            Map<String, Object> map = new HashMap<>();
            map.put("photoId", p.getId());
            map.put("takenDate", p.getTakenDate());
            map.put("locationName", p.getLocationName());
            map.put("city", p.getCity());
            map.put("country", p.getCountry());
            map.put("province", p.getProvince());
            map.put("description", p.getDescription());
            map.put("storagePath", p.getStoragePath());
            if (p.getUploader() != null) {
                map.put("uploaderId", p.getUploader().getId());
                map.put("uploaderNickname", p.getUploader().getNickname());
            }
            return map;
        } catch (Exception e) {
            log.error("[AI-TOOL] describePhoto 失败", e);
            return Map.of("error", "查询失败：" + e.getMessage());
        }
    }

    // ==================== 写操作（二次确认） ====================

    /**
     * 删除照片（第一步：返回确认 token）。
     * <p>
     * 注意：只有照片上传者本人才能删除。Service 内部已校验 `photo.userId == currentUserId`。
     */
    @Tool("删除一张照片（第一步：返回确认 token，不直接写入）。OSS 文件一并清理，不可逆；需要用户在前端二次确认。")
    public Map<String, Object> prepareDeletePhoto(@P("照片 ID") Long photoId) {
        Long userId = AiUserContext.requireUserId();
        log.info("[AI-TOOL] prepareDeletePhoto userId={}, photoId={}", userId, photoId);
        if (photoId == null) return Map.of("error", "photoId 不能为空");

        // 预览：取元数据回显给用户
        Result<PhotoDetailVO> detail = photoService.getPhotoDetail(userId.intValue(), photoId);
        Map<String, Object> preview = new LinkedHashMap<>();
        preview.put("action", "delete");
        preview.put("photoId", photoId);
        if (detail != null && detail.getData() != null) {
            PhotoDetailVO d = detail.getData();
            preview.put("takenDate", d.getTakenDate());
            preview.put("locationName", d.getLocationName());
            preview.put("city", d.getCity());
            preview.put("description", d.getDescription());
            preview.put("storagePath", d.getStoragePath());
            if (d.getUploader() != null) {
                preview.put("uploaderId", d.getUploader().getId());
                preview.put("uploaderNickname", d.getUploader().getNickname());
            }
        }
        preview.put("warning", "OSS 文件和数据库记录会一并清理，不可恢复");

        String token = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        pendingMap.put(token, new PendingDeletePhoto(userId.intValue(), photoId, System.currentTimeMillis()));
        new Timer().schedule(new TimerTask() {
            @Override public void run() { pendingMap.remove(token); }
        }, 60 * 60 * 1000L);

        return Map.of(
                "status", "CONFIRM_REQUIRED",
                "confirm_token", token,
                "preview", preview,
                "hint", "⚠️ 尚未删除！这是不可逆操作（OSS 文件一并清理）。请向用户复述以上照片信息并明确请求用户确认。"
                        + "用户明确说'确认/同意/删吧'后调用 confirmDeletePhoto(token=\"" + token + "\")。"
                        + "在用户确认前禁止告诉用户'已删除'。"
        );
    }

    /**
     * 删除照片（第二步：用户已确认后真正写入）
     */
    @Tool("删除照片第二步：用户已确认后真正写入。必须传入 prepareDeletePhoto 返回的 confirm_token。")
    public Map<String, Object> confirmDeletePhoto(@P("prepareDeletePhoto 返回的确认 token") String confirmToken) {
        Long userId = AiUserContext.requireUserId();
        log.info("[AI-TOOL] confirmDeletePhoto userId={}, token={}", userId, confirmToken);
        if (confirmToken == null || confirmToken.isBlank()) return Map.of("error", "confirm_token 不能为空");

        PendingDeletePhoto pending = pendingMap.remove(confirmToken);
        if (pending == null) return Map.of("error", "确认凭证无效或已过期，请重新发起删除");
        if (!pending.userId.equals(userId.intValue())) return Map.of("error", "确认凭证归属错误");
        if (System.currentTimeMillis() - pending.createdAtMs > 10 * 60 * 1000L) {
            return Map.of("error", "确认凭证已过期，请重新发起删除");
        }

        try {
            Result<Void> r = photoService.deletePhoto(userId.intValue(), pending.photoId);
            if (r == null || !r.isSuccess()) {
                return Map.of("error", r == null ? "null" : r.getMessage());
            }
            return Map.of(
                    "status", "DELETED",
                    "action", "delete",
                    "photoId", pending.photoId
            );
        } catch (Exception e) {
            log.error("[AI-TOOL] confirmDeletePhoto 失败", e);
            return Map.of("error", "删除失败：" + e.getMessage());
        }
    }

    // ==================== 内部 ====================

    private boolean matchesKeyword(TimelinePhotoVO p, String kw) {
        return contains(p.getCity(), kw)
                || contains(p.getProvence(), kw)
                || contains(p.getLocationName(), kw)
                || contains(p.getCountry(), kw)
                || contains(p.getDescription(), kw);
    }

    private boolean contains(String s, String kw) {
        return s != null && s.toLowerCase().contains(kw);
    }

    private Map<String, Object> toMap(TimelinePhotoVO p) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", "image");
        map.put("photoId", p.getId());
        map.put("imageUrl", p.getStoragePath());
        map.put("takenDate", p.getTakenDate());
        map.put("locationName", p.getLocationName());
        map.put("city", p.getCity());
        map.put("province", p.getProvence());
        map.put("country", p.getCountry());
        map.put("description", p.getDescription());
        map.put("storagePath", p.getStoragePath());
        return map;
    }

    private LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return LocalDate.parse(s.trim());
        } catch (Exception e) {
            log.warn("[AI-TOOL] 日期解析失败: {}", s);
            return null;
        }
    }

    /** 待删除的照片（含过期机制） */
    private record PendingDeletePhoto(Integer userId, Long photoId, long createdAtMs) {}
}