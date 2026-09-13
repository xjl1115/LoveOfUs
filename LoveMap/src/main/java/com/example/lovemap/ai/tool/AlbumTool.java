package com.example.lovemap.ai.tool;

import com.example.lovemap.ai.context.AiUserContext;
import com.example.lovemap.common.Result;
import com.example.lovemap.model.vo.AlbumVO;
import com.example.lovemap.service.AlbumService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 相册 AI 工具
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlbumTool {

    private final AlbumService albumService;

    /** 二次确认 token 缓存：token -> PendingAlbumOp */
    private final Map<String, PendingAlbumOp> pendingMap = new ConcurrentHashMap<>();

    /**
     * 查询所有相册
     */
    @Tool("查询当前用户的所有相册（不含照片内容）。")
    public List<Map<String, Object>> listAlbums() {
        Long userId = AiUserContext.requireUserId();
        log.info("[AI-TOOL] listAlbums userId={}", userId);
        try {
            Result<List<AlbumVO>> result = albumService.listAlbums(userId.intValue());
            if (result == null || result.getData() == null) return List.of();
            List<Map<String, Object>> out = new ArrayList<>();
            for (AlbumVO vo : result.getData()) {
                out.add(toMap(vo));
            }
            return out;
        } catch (Exception e) {
            log.error("[AI-TOOL] listAlbums 失败", e);
            return List.of(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 按名称搜索相册（模糊匹配）
     */
    @Tool("按名称模糊搜索相册，例如：'三亚'、'婚礼'。")
    public List<Map<String, Object>> searchAlbumByName(@P("相册名称关键词") String keyword) {
        Long userId = AiUserContext.requireUserId();
        log.info("[AI-TOOL] searchAlbumByName userId={}, keyword='{}'", userId, keyword);
        try {
            Result<List<AlbumVO>> result = albumService.listAlbums(userId.intValue());
            if (result == null || result.getData() == null) return List.of();
            String kw = (keyword == null ? "" : keyword.trim().toLowerCase());
            List<Map<String, Object>> out = new ArrayList<>();
            for (AlbumVO vo : result.getData()) {
                if (kw.isEmpty() || (vo.getName() != null && vo.getName().toLowerCase().contains(kw))) {
                    out.add(toMap(vo));
                }
            }
            return out;
        } catch (Exception e) {
            log.error("[AI-TOOL] searchAlbumByName 失败", e);
            return List.of(Map.of("error", e.getMessage()));
        }
    }

    // ==================== 写操作（二次确认） ====================

    /**
     * 把照片加入相册（第一步：返回确认 token）。
     * <p>
     * photoIds 必须是当前用户/情侣组能看到的，由 AI 通过 `searchPhotos` 拿到后传入。
     */
    @Tool("把若干张照片加入指定相册（第一步：返回确认 token，不直接写入）。需要用户在前端二次确认。photoIds 传 JSON 数组字符串，例如 \"[101,102,103]\"；传空数组或 null 表示按相册当前状态不变。")
    public Map<String, Object> prepareAddPhotosToAlbum(
            @P("相册 ID") Long albumId,
            @P("照片 ID 列表，JSON 数组字符串，例如 \"[101,102,103]\"") String photoIdsJson) {

        Long userId = AiUserContext.requireUserId();
        log.info("[AI-TOOL] prepareAddPhotosToAlbum userId={}, albumId={}, photoIdsJson={}",
                userId, albumId, photoIdsJson);

        if (albumId == null) return Map.of("error", "albumId 不能为空");
        List<Long> photoIds = parseLongList(photoIdsJson);
        if (photoIds.isEmpty()) return Map.of("error", "photoIds 不能为空，请传入至少 1 个照片 ID");

        Map<String, Object> permError = checkAlbumPermission(userId, albumId);
        if (permError != null) return permError;

        String token = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        pendingMap.put(token, new PendingAlbumOp(
                userId.intValue(), "addPhotos", albumId, photoIds, null, null,
                System.currentTimeMillis()));
        scheduleExpire(token);

        return Map.of(
                "status", "CONFIRM_REQUIRED",
                "confirm_token", token,
                "preview", Map.of(
                        "action", "addPhotos",
                        "albumId", albumId,
                        "photoIds", photoIds,
                        "photoCount", photoIds.size()),
                "hint", "⚠️ 尚未加入相册！请向用户复述" + photoIds.size() + " 张照片即将加入相册 albumId="
                        + albumId + "，并明确请求用户确认。用户明确说'确认/同意'后调用 confirmAlbumOp(token=\""
                        + token + "\") 真正写入。在用户确认前禁止告诉用户'已加入/已完成'。"
        );
    }

    /**
     * 从相册移除一张照片（第一步：返回确认 token）
     */
    @Tool("从指定相册移除一张照片（第一步：返回确认 token，不直接写入）。需要用户在前端二次确认。")
    public Map<String, Object> prepareRemovePhotoFromAlbum(
            @P("相册 ID") Long albumId,
            @P("照片 ID") Long photoId) {

        Long userId = AiUserContext.requireUserId();
        log.info("[AI-TOOL] prepareRemovePhotoFromAlbum userId={}, albumId={}, photoId={}",
                userId, albumId, photoId);

        if (albumId == null || photoId == null) return Map.of("error", "albumId 和 photoId 均不能为空");

        Map<String, Object> permError = checkAlbumPermission(userId, albumId);
        if (permError != null) return permError;

        String token = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        pendingMap.put(token, new PendingAlbumOp(
                userId.intValue(), "removePhoto", albumId, List.of(photoId), null, null,
                System.currentTimeMillis()));
        scheduleExpire(token);

        return Map.of(
                "status", "CONFIRM_REQUIRED",
                "confirm_token", token,
                "preview", Map.of(
                        "action", "removePhoto",
                        "albumId", albumId,
                        "photoId", photoId),
                "hint", "⚠️ 尚未移除！请向用户确认将照片 photoId=" + photoId
                        + " 从相册 albumId=" + albumId + " 移除。用户确认后调用 confirmAlbumOp(token=\""
                        + token + "\")。在用户确认前禁止告诉用户'已移除'。"
        );
    }

    /**
     * 修改相册（第一步：返回确认 token）。name / description 至少传一个；都不传返回错误。
     */
    @Tool("修改相册的名称或描述（第一步：返回确认 token，不直接写入）。需要用户在前端二次确认。name 和 description 至少传一个。")
    public Map<String, Object> prepareUpdateAlbum(
            @P("相册 ID") Long albumId,
            @P("新相册名称，传 null 表示不改") String name,
            @P("新相册描述，传 null 表示不改") String description) {

        Long userId = AiUserContext.requireUserId();
        log.info("[AI-TOOL] prepareUpdateAlbum userId={}, albumId={}, name='{}', description='{}'",
                userId, albumId, name, description);

        if (albumId == null) return Map.of("error", "albumId 不能为空");
        if ((name == null || name.isBlank()) && (description == null)) {
            return Map.of("error", "name 和 description 至少需要传一个");
        }
        Map<String, Object> permError = checkAlbumPermission(userId, albumId);
        if (permError != null) return permError;

        String newName = (name == null || name.isBlank()) ? null : name.trim();
        String newDesc = description;
        if (newName != null && newName.length() > 50) {
            return Map.of("error", "相册名称长度不能超过 50");
        }

        String token = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        pendingMap.put(token, new PendingAlbumOp(
                userId.intValue(), "update", albumId, null, newName, newDesc,
                System.currentTimeMillis()));
        scheduleExpire(token);

        Map<String, Object> preview = new HashMap<>();
        preview.put("action", "update");
        preview.put("albumId", albumId);
        if (newName != null) preview.put("newName", newName);
        if (newDesc != null) preview.put("newDescription", newDesc);

        return Map.of(
                "status", "CONFIRM_REQUIRED",
                "confirm_token", token,
                "preview", preview,
                "hint", "⚠️ 尚未修改！请向用户复述以上变更并明确请求用户确认。用户确认后调用 confirmAlbumOp(token=\""
                        + token + "\")。在用户确认前禁止告诉用户'已修改'。"
        );
    }

    /**
     * 删除相册（第一步：返回确认 token）
     */
    @Tool("删除相册（第一步：返回确认 token，不直接写入）。相册内独占照片会被物理删除并清理 OSS，需要用户在前端二次确认。")
    public Map<String, Object> prepareDeleteAlbum(@P("相册 ID") Long albumId) {
        Long userId = AiUserContext.requireUserId();
        log.info("[AI-TOOL] prepareDeleteAlbum userId={}, albumId={}", userId, albumId);

        if (albumId == null) return Map.of("error", "albumId 不能为空");
        Map<String, Object> permError = checkAlbumPermission(userId, albumId);
        if (permError != null) return permError;

        // 顺手查一下相册名方便回显
        Result<List<AlbumVO>> r = albumService.listAlbums(userId.intValue());
        String albumName = null;
        if (r != null && r.getData() != null) {
            for (AlbumVO vo : r.getData()) {
                if (vo.getId().equals(albumId)) { albumName = vo.getName(); break; }
            }
        }

        String token = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        pendingMap.put(token, new PendingAlbumOp(
                userId.intValue(), "delete", albumId, null, null, null,
                System.currentTimeMillis()));
        scheduleExpire(token);

        Map<String, Object> preview = new HashMap<>();
        preview.put("action", "delete");
        preview.put("albumId", albumId);
        if (albumName != null) preview.put("albumName", albumName);
        preview.put("warning", "相册内独占照片会被永久删除，且 OSS 文件一并清理");

        return Map.of(
                "status", "CONFIRM_REQUIRED",
                "confirm_token", token,
                "preview", preview,
                "hint", "⚠️ 尚未删除！这是一个不可逆操作。请向用户强调：相册内独占照片会被永久删除。"
                        + "用户明确说'确认/同意/删吧'后调用 confirmAlbumOp(token=\"" + token + "\")。"
                        + "在用户确认前禁止告诉用户'已删除'。"
        );
    }

    /**
     * 创建相册（第一步：返回确认 token）
     */
    @Tool("创建一个新相册（第一步：返回确认 token，不直接写入）。需要用户在前端二次确认。name 必填（1-50 字符），description 可选。")
    public Map<String, Object> prepareCreateAlbum(
            @P("相册名称，1-50 字符") String name,
            @P("相册描述，可选") String description) {

        Long userId = AiUserContext.requireUserId();
        log.info("[AI-TOOL] prepareCreateAlbum userId={}, name='{}', description='{}'",
                userId, name, description);

        if (name == null || name.isBlank()) return Map.of("error", "相册名称不能为空");
        String trimmed = name.trim();
        if (trimmed.length() < 1 || trimmed.length() > 50) {
            return Map.of("error", "相册名称长度需在 1-50 个字符之间");
        }

        String token = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        pendingMap.put(token, new PendingAlbumOp(
                userId.intValue(), "create", null, null, trimmed,
                description == null ? "" : description,
                System.currentTimeMillis()));
        scheduleExpire(token);

        return Map.of(
                "status", "CONFIRM_REQUIRED",
                "confirm_token", token,
                "preview", Map.of(
                        "action", "create",
                        "name", trimmed,
                        "description", description == null ? "" : description),
                "hint", "⚠️ 尚未创建！请向用户复述相册名称和描述并明确请求用户确认。"
                        + "用户确认后调用 confirmAlbumOp(token=\"" + token + "\")。"
                        + "在用户确认前禁止告诉用户'已创建'。"
        );
    }

    /**
     * 二次确认第二步：执行上一步 prepare 返回的操作
     */
    @Tool("相册写操作第二步：用户已确认后真正写入。必须传入 prepareAddPhotosToAlbum / prepareRemovePhotoFromAlbum / prepareUpdateAlbum / prepareDeleteAlbum / prepareCreateAlbum 返回的 confirm_token。")
    public Map<String, Object> confirmAlbumOp(@P("prepareXxx 返回的确认 token") String confirmToken) {
        Long userId = AiUserContext.requireUserId();
        log.info("[AI-TOOL] confirmAlbumOp userId={}, token={}", userId, confirmToken);

        if (confirmToken == null || confirmToken.isBlank()) {
            return Map.of("error", "confirm_token 不能为空");
        }
        PendingAlbumOp pending = pendingMap.remove(confirmToken);
        if (pending == null) return Map.of("error", "确认凭证无效或已过期，请重新发起操作");
        if (!pending.userId.equals(userId.intValue())) {
            return Map.of("error", "确认凭证归属错误");
        }
        // 10 分钟过期
        if (System.currentTimeMillis() - pending.createdAtMs > 10 * 60 * 1000L) {
            return Map.of("error", "确认凭证已过期，请重新发起操作");
        }

        try {
            switch (pending.op) {
                case "addPhotos" -> {
                    Result<Void> r = albumService.addPhotosToAlbum(userId.intValue(), pending.albumId, pending.photoIds);
                    return toResultMap(r, "ADDED", Map.of(
                            "action", "addPhotos",
                            "albumId", pending.albumId,
                            "photoCount", pending.photoIds.size()));
                }
                case "removePhoto" -> {
                    Result<Void> r = albumService.removePhotoFromAlbum(
                            userId.intValue(), pending.albumId, pending.photoIds.get(0));
                    return toResultMap(r, "REMOVED", Map.of(
                            "action", "removePhoto",
                            "albumId", pending.albumId,
                            "photoId", pending.photoIds.get(0)));
                }
                case "update" -> {
                    com.example.lovemap.model.dto.AlbumUpdateDTO dto = new com.example.lovemap.model.dto.AlbumUpdateDTO();
                    dto.setName(pending.newName);
                    dto.setDescription(pending.newDescription);
                    Result<AlbumVO> r = albumService.updateAlbum(userId.intValue(), pending.albumId, dto);
                    if (r == null || !r.isSuccess() || r.getData() == null) {
                        return Map.of("error", r == null ? "null" : r.getMessage());
                    }
                    Map<String, Object> resp = toMap(r.getData());
                    resp.put("status", "UPDATED");
                    resp.put("action", "update");
                    return resp;
                }
                case "delete" -> {
                    Result<Void> r = albumService.deleteAlbum(userId.intValue(), pending.albumId);
                    return toResultMap(r, "DELETED", Map.of(
                            "action", "delete",
                            "albumId", pending.albumId));
                }
                case "create" -> {
                    com.example.lovemap.model.dto.AlbumCreateDTO dto = new com.example.lovemap.model.dto.AlbumCreateDTO();
                    dto.setName(pending.newName);
                    dto.setDescription(pending.newDescription);
                    Result<AlbumVO> r = albumService.createAlbum(userId.intValue(), dto);
                    if (r == null || !r.isSuccess() || r.getData() == null) {
                        return Map.of("error", r == null ? "null" : r.getMessage());
                    }
                    Map<String, Object> resp = toMap(r.getData());
                    resp.put("status", "CREATED");
                    resp.put("action", "create");
                    return resp;
                }
                default -> {
                    return Map.of("error", "未知操作类型：" + pending.op);
                }
            }
        } catch (Exception e) {
            log.error("[AI-TOOL] confirmAlbumOp 失败 op={}", pending.op, e);
            return Map.of("error", "执行失败：" + e.getMessage());
        }
    }

    // ==================== 内部 ====================

    private Map<String, Object> toMap(AlbumVO vo) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", vo.getId());
        map.put("name", vo.getName());
        map.put("description", vo.getDescription());
        map.put("coverUrl", vo.getCoverPhotoUrl());
        map.put("photoCount", vo.getPhotoCount());
        map.put("createdAt", vo.getCreatedAt() != null ? vo.getCreatedAt().toString() : null);
        return map;
    }

    /**
     * 校验当前用户对 albumId 有操作权限：通过 listAlbums（已按 groupId/userId 隔离）确认相册存在且属于当前用户。
     */
    private Map<String, Object> checkAlbumPermission(Long userId, Long albumId) {
        Result<List<AlbumVO>> list = albumService.listAlbums(userId.intValue());
        if (list == null || list.getData() == null) {
            return Map.of("error", "无法校验相册权限");
        }
        boolean owned = list.getData().stream().anyMatch(v -> v.getId().equals(albumId));
        if (!owned) return Map.of("error", "相册不存在或无权访问：albumId=" + albumId);
        return null;
    }

    /** 解析 JSON 数组字符串为 List<Long>，解析失败返回空列表 */
    private List<Long> parseLongList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
            List<Number> raw = om.readValue(json, List.class);
            return raw.stream().map(Number::longValue).collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("[AI-TOOL] photoIdsJson 解析失败: {}", json, e);
            return List.of();
        }
    }

    /** 1 小时清理（远大于 10 分钟有效期，留出重试空间） */
    private void scheduleExpire(String token) {
        new Timer().schedule(new TimerTask() {
            @Override public void run() { pendingMap.remove(token); }
        }, 60 * 60 * 1000L);
    }

    /** 把 Result<Void> 包装成 Map<String,Object> */
    private Map<String, Object> toResultMap(Result<Void> r, String okStatus, Map<String, Object> extra) {
        if (r == null || !r.isSuccess()) {
            return Map.of("error", r == null ? "null" : r.getMessage());
        }
        Map<String, Object> resp = new HashMap<>(extra);
        resp.put("status", okStatus);
        return resp;
    }

    /** 待执行的相册操作 */
    private record PendingAlbumOp(
            Integer userId,
            String op,             // addPhotos / removePhoto / update / delete / create
            Long albumId,
            List<Long> photoIds,
            String newName,
            String newDescription,
            long createdAtMs
    ) {}
}