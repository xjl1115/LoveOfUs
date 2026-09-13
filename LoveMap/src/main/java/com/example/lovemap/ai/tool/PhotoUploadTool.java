package com.example.lovemap.ai.tool;

import com.example.lovemap.ai.context.AiUserContext;
import com.example.lovemap.common.Result;
import com.example.lovemap.model.dto.AlbumCreateDTO;
import com.example.lovemap.model.vo.AlbumVO;
import com.example.lovemap.service.AlbumService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 照片上传引导 AI 工具
 * <p>
 * 设计目标：让 AI 在聊天中引导用户**逐步提供**照片元数据，并**智能匹配/创建相册**，
 * 最终生成一份"上传指令"返回给前端，由前端调用现有 /api/photos/upload 接口完成
 * OSS 和数据库写入（AI Tool 本身不能直接传二进制文件）。
 * <p>
 * 流程：
 * <ol>
 *   <li>collectUploadField(field, value)  ：逐步收集字段（拍摄日期/国家/省份/市区/景点/描述/相册名）</li>
 *   <li>resolveAlbum(albumName)          ：相册名查 DB：存在则用、不存在则自动创建</li>
 *   <li>prepareUpload()                  ：字段齐全且相册已就绪，生成"上传指令" token</li>
 *   <li>confirmUpload(token)             ：用户在前端确认后真正落库（调 AlbumService.addPhotosToAlbum 把照片加入相册）</li>
 * </ol>
 * <p>
 * 注意：真正的"文件二进制上传到 OSS"必须由前端在用户选择文件那一刻完成——
 * 本工具只负责收集元数据、决定相册 ID、并在用户确认后把 photoId 加入相册。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PhotoUploadTool {

    private final AlbumService albumService;

    /** 用户当前会话的上传草稿：userId -> 草稿。单次会话内有效。 */
    private final Map<Integer, UploadDraft> draftMap = new ConcurrentHashMap<>();

    /** 二次确认 token -> 待确认的上传指令。单次会话内有效。 */
    private final Map<String, PendingUpload> pendingMap = new ConcurrentHashMap<>();

    /**
     * 必填字段：takenDate / country / province / city / locationName / albumName
     * 可选字段：description
     */
    private static final List<String> REQUIRED_FIELDS = List.of(
            "takenDate", "country", "province", "city", "locationName", "albumName"
    );

    private static final Map<String, String> FIELD_LABEL = Map.ofEntries(
            Map.entry("takenDate", "拍摄日期(格式 yyyy-MM-dd)"),
            Map.entry("country", "国家名称(例如:中国)"),
            Map.entry("province", "省份名称(例如:海南、北京)"),
            Map.entry("city", "市区名称(例如:三亚)"),
            Map.entry("locationName", "景点名称(例如:亚龙湾)"),
            Map.entry("albumName", "相册名称(例如:三亚之旅)"),
            Map.entry("description", "描述(可选,最多 200 字)")
    );

    // ==================== 引导式收集 ====================

    /**
     * 第一步：收集单个字段值到草稿
     * <p>
     * AI 每拿到用户回答的一个字段，就调一次本方法。
     * field 取值：takenDate / country / province / city / locationName / albumName / description
     */
    @Tool("逐步收集照片上传所需的元数据字段。每收到用户回答的一个字段就调用一次：field 取值 takenDate/country/province/city/locationName/albumName/description。")
    public Map<String, Object> collectUploadField(
            @P("字段名：takenDate-拍摄日期yyyy-MM-dd，country-国家，province-省份，city-市区，locationName-景点名称，albumName-相册名称，description-描述(可选)") String field,
            @P("字段值（字符串）") String value) {

        Integer userId = AiUserContext.requireUserId().intValue();
        log.info("[AI-TOOL] collectUploadField userId={}, field={}, value={}", userId, field, value);

        if (field == null || field.isBlank()) return Map.of("error", "field 不能为空");
        UploadDraft draft = draftMap.computeIfAbsent(userId, k -> new UploadDraft());
        String key = field.trim();

        try {
            switch (key) {
                case "takenDate" -> {
                    if (value == null || value.isBlank()) return Map.of("error", "拍摄日期不能为空");
                    try {
                        draft.takenDate = LocalDate.parse(value.trim());
                    } catch (DateTimeParseException e) {
                        return Map.of("error", "日期格式错误，应为 yyyy-MM-dd，例如 2026-08-27");
                    }
                }
                case "country" -> {
                    String v = value == null ? "" : value.trim();
                    if (v.isEmpty()) return Map.of("error", "国家不能为空");
                    if (v.length() > 30) return Map.of("error", "国家名称过长");
                    draft.country = v;
                }
                case "province" -> {
                    String v = value == null ? "" : value.trim();
                    if (v.isEmpty()) return Map.of("error", "省份不能为空");
                    if (v.length() > 30) return Map.of("error", "省份名称过长");
                    draft.province = v;
                }
                case "city" -> {
                    String v = value == null ? "" : value.trim();
                    if (v.isEmpty()) return Map.of("error", "市区不能为空");
                    if (v.length() > 30) return Map.of("error", "市区名称过长");
                    draft.city = v;
                }
                case "locationName" -> {
                    String v = value == null ? "" : value.trim();
                    if (v.isEmpty()) return Map.of("error", "景点名称不能为空");
                    if (v.length() > 100) return Map.of("error", "景点名称过长(最多 100 字)");
                    draft.locationName = v;
                }
                case "albumName" -> {
                    String v = value == null ? "" : value.trim();
                    if (v.isEmpty()) return Map.of("error", "相册名称不能为空");
                    if (v.length() > 50) return Map.of("error", "相册名称过长(最多 50 字)");
                    draft.albumName = v;
                }
                case "description" -> {
                    String v = value == null ? "" : value.trim();
                    if (v.length() > 200) v = v.substring(0, 200);
                    draft.description = v;
                }
                default -> {
                    return Map.of("error", "未知字段：" + field + "，仅支持 takenDate/country/province/city/locationName/albumName/description");
                }
            }
        } catch (Exception e) {
            log.error("[AI-TOOL] collectUploadField 失败", e);
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
    @Tool("检查当前照片上传草稿。返回已收集字段、缺失字段列表、以及下一步动作建议。")
    public Map<String, Object> checkUploadDraft() {
        Integer userId = AiUserContext.requireUserId().intValue();
        log.info("[AI-TOOL] checkUploadDraft userId={}", userId);

        UploadDraft draft = draftMap.get(userId);
        if (draft == null) {
            return Map.of(
                    "status", "EMPTY",
                    "missing_fields", REQUIRED_FIELDS,
                    "hint", "尚未开始收集，请先调用 collectUploadField 向用户询问「" + FIELD_LABEL.get("takenDate") + "」"
            );
        }

        List<String> missing = new ArrayList<>();
        if (draft.takenDate == null) missing.add("takenDate");
        if (draft.country == null) missing.add("country");
        if (draft.province == null) missing.add("province");
        if (draft.city == null) missing.add("city");
        if (draft.locationName == null) missing.add("locationName");
        if (draft.albumName == null) missing.add("albumName");

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("status", missing.isEmpty() ? "READY" : "INCOMPLETE");
        resp.put("currentDraft", draftView(draft));
        resp.put("missing_fields", missing);
        if (missing.isEmpty()) {
            resp.put("hint", "草稿 status=READY，必填字段已齐全。【必须】下一步调用 resolveAlbum(albumName='" + draft.albumName
                    + "') 让后端智能匹配/创建相册（返回 albumId）；拿到 albumId 后立刻调用 prepareUpload 生成确认 token 推送给用户二次确认；"
                    + "确认完成后调用 confirmUpload(token, photoIds) 把照片加入相册。**禁止在 prepare 之前结束对话或自行总结**。");
        } else {
            resp.put("hint", "草稿 status=INCOMPLETE，还缺少字段：" + missing + "。请按顺序逐个向用户询问并调用 collectUploadField 收集。");
        }
        return resp;
    }

    /**
     * 第三步：智能匹配/创建相册
     * <p>
     * 逻辑：先按名称在当前用户已有相册中模糊匹配；命中则复用，未命中则自动创建新相册。
     * 返回 albumId 写入草稿。
     */
    @Tool("根据相册名在数据库中查找或自动创建相册。命中已有相册则复用，未命中则新建。返回 albumId 与是否新建标记。")
    public Map<String, Object> resolveAlbum(@P("相册名称") String albumName) {
        Integer userId = AiUserContext.requireUserId().intValue();
        log.info("[AI-TOOL] resolveAlbum userId={}, albumName='{}'", userId, albumName);

        if (albumName == null || albumName.isBlank()) {
            return Map.of("error", "相册名称不能为空");
        }
        String name = albumName.trim();
        if (name.length() > 50) {
            return Map.of("error", "相册名称过长(最多 50 字)");
        }
        UploadDraft draft = draftMap.get(userId);
        if (draft == null) {
            return Map.of("error", "尚未收集字段，请先调用 collectUploadField");
        }

        try {
            // 1. 查询当前用户所有相册，按名称模糊匹配
            Result<List<AlbumVO>> r = albumService.listAlbums(userId);
            if (r == null || r.getData() == null) {
                return Map.of("error", "查询相册列表失败");
            }
            String kw = name.toLowerCase();
            AlbumVO hit = null;
            for (AlbumVO vo : r.getData()) {
                if (vo.getName() != null && vo.getName().toLowerCase().equals(kw)) {
                    hit = vo; // 完全相等优先
                    break;
                }
            }
            if (hit == null) {
                for (AlbumVO vo : r.getData()) {
                    if (vo.getName() != null && vo.getName().toLowerCase().contains(kw)) {
                        hit = vo; // 包含匹配
                        break;
                    }
                }
            }

            // 2. 命中则复用
            if (hit != null) {
                draft.albumId = hit.getId();
                draft.albumCreated = false;
                Map<String, Object> resp = new LinkedHashMap<>();
                resp.put("status", "ALBUM_REUSED");
                resp.put("albumId", hit.getId());
                resp.put("albumName", hit.getName());
                resp.put("created", false);
                resp.put("hint", "已找到相册《" + hit.getName() + "》(id=" + hit.getId()
                        + ")，可复用。下一步：调用 prepareUpload 生成上传指令。");
                return resp;
            }

            // 3. 未命中则创建
            AlbumCreateDTO dto = new AlbumCreateDTO();
            dto.setName(name);
            dto.setDescription("由 AI 助手自动创建");
            Result<AlbumVO> createRes = albumService.createAlbum(userId, dto);
            if (createRes == null || !createRes.isSuccess() || createRes.getData() == null) {
                return Map.of("error", createRes == null ? "null" : createRes.getMessage());
            }
            AlbumVO newAlbum = createRes.getData();
            draft.albumId = newAlbum.getId();
            draft.albumCreated = true;
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("status", "ALBUM_CREATED");
            resp.put("albumId", newAlbum.getId());
            resp.put("albumName", newAlbum.getName());
            resp.put("created", true);
            resp.put("hint", "未找到相册，已自动创建《" + newAlbum.getName() + "》(id="
                    + newAlbum.getId() + ")。【必须】下一步立刻调用 prepareUpload 生成确认 token 推送给用户二次确认；"
                    + "确认完成后调用 confirmUpload(token, photoIds) 把照片加入相册。**禁止在此步骤结束对话或自行总结**。");
            return resp;
        } catch (Exception e) {
            log.error("[AI-TOOL] resolveAlbum 失败", e);
            return Map.of("error", "相册处理失败：" + e.getMessage());
        }
    }

    /**
     * 第四步：字段齐全且相册已就绪后，生成"上传指令"（不直接写 DB）
     * <p>
     * 返回的 confirm_token 由前端在前端二次确认后回传，
     * 后端会调用 confirmUpload 把 photoId 加入相册（photoId 由前端上传文件到 OSS 后获得）。
     * <p>
     * 注意：本工具**不能**直接传二进制文件。真正的文件上传必须由前端完成：
     * 前端在用户点击 + 时已经把文件传到 OSS 拿到 photoId，
     * 这里仅生成"补全元数据并加入相册"的指令，由前端调 confirmUpload。
     */
    @Tool("当所有必填字段已收集、相册已 resolveAlbum 完成后调用，生成二次确认 token 并返回给前端弹窗。注意：仅在 checkUploadDraft 返回 status=READY 且 resolveAlbum 已成功后才调用。")
    public Map<String, Object> prepareUpload() {
        Long userIdLong = AiUserContext.requireUserId();
        Integer userId = userIdLong.intValue();
        log.info("[AI-TOOL] prepareUpload userId={}", userId);

        UploadDraft draft = draftMap.get(userId);
        if (draft == null) {
            return Map.of("error", "草稿不存在，请先调用 collectUploadField 收集字段");
        }
        // 校验必填字段
        List<String> missing = new ArrayList<>();
        if (draft.takenDate == null) missing.add("takenDate");
        if (draft.country == null) missing.add("country");
        if (draft.province == null) missing.add("province");
        if (draft.city == null) missing.add("city");
        if (draft.locationName == null) missing.add("locationName");
        if (draft.albumName == null) missing.add("albumName");
        if (!missing.isEmpty()) {
            return Map.of(
                    "error", "字段未齐全，缺少：" + missing,
                    "missing_fields", missing,
                    "hint", "请先逐个向用户询问缺失字段并调用 collectUploadField 收集"
            );
        }
        // 校验相册
        if (draft.albumId == null) {
            return Map.of(
                    "error", "相册尚未 resolveAlbum，请先调用 resolveAlbum(albumName='" + draft.albumName + "')",
                    "hint", "resolveAlbum 会在数据库中查找相册，没有则自动创建"
            );
        }

        String token = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        pendingMap.put(token, new PendingUpload(
                userId,
                draft.takenDate,
                draft.country,
                draft.province,
                draft.city,
                draft.locationName,
                draft.description == null ? "" : draft.description,
                draft.albumId,
                draft.albumCreated,
                System.currentTimeMillis()
        ));
        // 1 小时过期
        new java.util.Timer().schedule(new java.util.TimerTask() {
            @Override public void run() { pendingMap.remove(token); }
        }, 60 * 60 * 1000L);

        // 清理草稿（生成 token 后无需再保留）
        draftMap.remove(userId);

        Map<String, Object> preview = new LinkedHashMap<>();
        preview.put("takenDate", draft.takenDate.toString());
        preview.put("country", draft.country);
        preview.put("province", draft.province);
        preview.put("city", draft.city);
        preview.put("locationName", draft.locationName);
        preview.put("description", draft.description == null ? "" : draft.description);
        preview.put("albumId", draft.albumId);
        preview.put("albumName", draft.albumName);

        return Map.of(
                "status", "CONFIRM_REQUIRED",
                "confirm_token", token,
                "preview", preview,
                "hint", "请向用户复述以上信息并请求确认；用户在前端确认后调用 confirmUpload(token='" + token
                        + "', photoIds=[...]) 把照片加入相册。"
        );
    }

    /**
     * 第五步：用户在前端确认后，把 photoId 加入相册
     * <p>
     * photoIds 必须是前端上传文件到 OSS 后拿到的真实 photoId。
     * 后端会把这些 photoId 加入 prepareUpload 中已确定的相册。
     */
    @Tool("照片上传最后一步：用户已确认后真正把照片加入相册。photoIds 必须是前端上传文件到 OSS 后拿到的真实 photoId 列表(JSON 数组字符串)。")
    public Map<String, Object> confirmUpload(
            @P("prepareUpload 返回的确认 token") String confirmToken,
            @P("前端上传文件后拿到的 photoId 列表，JSON 数组字符串，例如 \"[101,102]\"") String photoIdsJson) {

        Long userId = AiUserContext.requireUserId();
        log.info("[AI-TOOL] confirmUpload userId={}, token={}, photoIdsJson={}", userId, confirmToken, photoIdsJson);

        if (confirmToken == null || confirmToken.isBlank()) {
            return Map.of("error", "confirm_token 不能为空");
        }
        PendingUpload pending = pendingMap.remove(confirmToken);
        if (pending == null) {
            return Map.of("error", "确认凭证无效或已过期，请重新发起");
        }
        if (!pending.userId.equals(userId.intValue())) {
            return Map.of("error", "确认凭证归属错误");
        }
        if (System.currentTimeMillis() - pending.createdAtMs > 10 * 60 * 1000L) {
            return Map.of("error", "确认凭证已过期，请重新发起");
        }

        List<Long> photoIds = parseLongList(photoIdsJson);
        if (photoIds.isEmpty()) {
            return Map.of("error", "photoIds 不能为空，请传入前端上传文件后拿到的真实 photoId 列表");
        }

        try {
            // 把照片加入相册
            Result<Void> r = albumService.addPhotosToAlbum(userId.intValue(), pending.albumId, photoIds);
            if (r == null || !r.isSuccess()) {
                return Map.of("error", r == null ? "null" : r.getMessage());
            }
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("status", "ALBUM_LINKED");
            resp.put("albumId", pending.albumId);
            resp.put("albumCreated", pending.albumCreated);
            resp.put("photoIds", photoIds);
            resp.put("photoCount", photoIds.size());
            return resp;
        } catch (Exception e) {
            log.error("[AI-TOOL] confirmUpload 失败", e);
            return Map.of("error", "写入失败：" + e.getMessage());
        }
    }

    // ==================== 内部 ====================

    private Map<String, Object> draftView(UploadDraft draft) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("takenDate", draft.takenDate == null ? null : draft.takenDate.toString());
        map.put("country", draft.country);
        map.put("province", draft.province);
        map.put("city", draft.city);
        map.put("locationName", draft.locationName);
        map.put("albumName", draft.albumName);
        map.put("albumId", draft.albumId);
        map.put("description", draft.description);
        return map;
    }

    /** 收集完一个字段后，提示下一步该问哪个字段；必填全收齐后强制 LLM 继续调 checkUploadDraft */
    private String nextFieldHint(UploadDraft draft) {
        if (draft.takenDate == null) return "下一步向用户询问「" + FIELD_LABEL.get("takenDate") + "」。";
        if (draft.country == null) return "下一步向用户询问「" + FIELD_LABEL.get("country") + "」。";
        if (draft.province == null) return "下一步向用户询问「" + FIELD_LABEL.get("province") + "」。";
        if (draft.city == null) return "下一步向用户询问「" + FIELD_LABEL.get("city") + "」。";
        if (draft.locationName == null) return "下一步向用户询问「" + FIELD_LABEL.get("locationName") + "」。";
        if (draft.albumName == null) return "下一步向用户询问「" + FIELD_LABEL.get("albumName") + "」。";
        if (draft.description == null) {
            return "必填字段已收集完毕。可以向用户询问「" + FIELD_LABEL.get("description")
                    + "」作为补充（用户说「不用/跳过」则不传 description）；"
                    + "询问后必须继续调用 checkUploadDraft 校验草稿,不要直接结束对话。";
        }
        // 必填+可选都齐全 → 强制 LLM 进入下一步
        return "全部字段已收集完毕。下一步【必须】调用 checkUploadDraft 校验草稿，"
                + "得到 status=READY 后立刻调用 resolveAlbum(albumName='" + draft.albumName
                + "') 让后端智能匹配/创建相册，然后调用 prepareUpload 生成确认 token。"
                + "**禁止在此步骤结束对话或自行总结**。";
    }

    /** 解析 JSON 数组字符串为 List<Long> */
    private List<Long> parseLongList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
            List<Number> raw = om.readValue(json, List.class);
            return raw.stream().map(Number::longValue).toList();
        } catch (Exception e) {
            log.warn("[AI-TOOL] photoIdsJson 解析失败: {}", json, e);
            return List.of();
        }
    }

    /** 草稿：单用户会话内有效 */
    private static class UploadDraft {
        LocalDate takenDate;
        String country;
        String province;
        String city;
        String locationName;
        String albumName;
        Long albumId;
        boolean albumCreated;
        String description;
    }

    /** 待确认的上传指令（含过期机制） */
    private record PendingUpload(
            Integer userId,
            LocalDate takenDate,
            String country,
            String province,
            String city,
            String locationName,
            String description,
            Long albumId,
            boolean albumCreated,
            long createdAtMs
    ) {}
}
