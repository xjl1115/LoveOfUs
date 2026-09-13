package com.example.lovemap.ai.tool;

import com.example.lovemap.ai.context.AiUserContext;
import com.example.lovemap.common.Result;
import com.example.lovemap.model.dto.NotificationSettingsDTO;
import com.example.lovemap.model.vo.NotificationSettingsVO;
import com.example.lovemap.service.UserService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 通知设置 AI 工具
 * <p>
 * 提供：
 * <ul>
 *   <li>getNotificationSettings        —— 读取当前通知设置</li>
 *   <li>prepareUpdateNotificationSettings / confirmUpdateNotificationSettings
 *       —— 修改通知设置（**二次确认**，支持部分更新：传 null 表示该字段不变）</li>
 * </ul>
 * <p>
 * 字段说明（与 NotificationSettingsVO 一致）：
 * <ul>
 *   <li>enablePush   —— 接收新消息通知总开关</li>
 *   <li>photoUpload  —— 伴侣上传照片提醒</li>
 *   <li>anniversary  —— 纪念日提醒</li>
 *   <li>email        —— 邮箱通知</li>
 *   <li>system       —— 系统公告</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationSettingsTool {

    private final UserService userService;

    /** 待执行的设置修改：token -> PendingSettings。仅在单次会话内有效，1 小时过期。 */
    private final Map<String, PendingSettings> pendingMap = new ConcurrentHashMap<>();

    // ==================== 查询 ====================

    /**
     * 读取当前用户的通知设置
     */
    @Tool("读取当前用户的通知设置：总开关/伴侣上传照片提醒/纪念日提醒/邮箱通知/系统公告。")
    public Map<String, Object> getNotificationSettings() {
        Long userId = AiUserContext.requireUserId();
        log.info("[AI-TOOL] getNotificationSettings userId={}", userId);
        try {
            Result<NotificationSettingsVO> r = userService.getNotificationSettings(userId.intValue());
            if (r == null || r.getData() == null) {
                return Map.of("error", "未获取到通知设置");
            }
            return toMap(r.getData());
        } catch (Exception e) {
            log.error("[AI-TOOL] getNotificationSettings 失败", e);
            return Map.of("error", "查询失败：" + e.getMessage());
        }
    }

    // ==================== 修改（二次确认） ====================

    /**
     * 修改通知设置（**二次确认** 第一步）。
     * <p>
     * 任意字段传 null 表示该字段不变（部分更新语义，与 Service.updateNotificationSettings 一致）。
     * 至少需要传入一个非 null 字段。
     */
    @Tool("修改通知设置（第一步：返回确认 token，不直接写入）。支持部分更新：任意字段传 null 表示不变；至少传 1 个非 null 字段。需要用户在前端二次确认。")
    public Map<String, Object> prepareUpdateNotificationSettings(
            @P("接收新消息通知总开关，传 null 表示不变") Boolean enablePush,
            @P("伴侣上传照片提醒，传 null 表示不变") Boolean photoUpload,
            @P("纪念日提醒，传 null 表示不变") Boolean anniversary,
            @P("邮箱通知，传 null 表示不变") Boolean email,
            @P("系统公告，传 null 表示不变") Boolean system) {

        Long userId = AiUserContext.requireUserId();
        log.info("[AI-TOOL] prepareUpdateNotificationSettings userId={}, enablePush={}, photoUpload={}, anniversary={}, email={}, system={}",
                userId, enablePush, photoUpload, anniversary, email, system);

        // 至少传一个非 null 字段
        if (enablePush == null && photoUpload == null && anniversary == null && email == null && system == null) {
            return Map.of("error", "至少需要传入一个要修改的字段（enablePush / photoUpload / anniversary / email / system）");
        }

        // 读取当前设置用于预览"原值→新值"
        Result<NotificationSettingsVO> cur = userService.getNotificationSettings(userId.intValue());
        NotificationSettingsVO current = (cur != null && cur.getData() != null) ? cur.getData() : new NotificationSettingsVO();

        String token = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        pendingMap.put(token, new PendingSettings(
                userId.intValue(),
                enablePush, photoUpload, anniversary, email, system,
                System.currentTimeMillis()
        ));
        new Timer().schedule(new TimerTask() {
            @Override public void run() { pendingMap.remove(token); }
        }, 60 * 60 * 1000L);

        Map<String, Object> preview = new LinkedHashMap<>();
        preview.put("enablePush",   diffLine("enablePush",   current.getEnablePush(),   enablePush));
        preview.put("photoUpload",  diffLine("photoUpload",  current.getPhotoUpload(),  photoUpload));
        preview.put("anniversary",  diffLine("anniversary",  current.getAnniversary(),  anniversary));
        preview.put("email",        diffLine("email",        current.getEmail(),        email));
        preview.put("system",       diffLine("system",       current.getSystem(),       system));

        return Map.of(
                "status", "CONFIRM_REQUIRED",
                "confirm_token", token,
                "preview", preview,
                "hint",
                    "🚨 数据库/Redis 当前还**完全没动过**！上面 preview 只是'打算改什么'的预览，"
                  + "不是结果。\n"
                  + "【你接下来必须做的事 - 按顺序】\n"
                  + "1) 先把 preview 里'有 changed=true 的每一行'用自然语言复述给用户："
                  + "   '当前 xxx 是 A，要改成 B，对吗？'\n"
                  + "2) **等用户亲口明确说'确认/同意/好的/可以/改吧/OK'**；"
                  + "   如果用户说'等一下/再想想/不要/拒绝'，就停在这里，**不要**继续。\n"
                  + "3) 用户确认后，**在本次回复里立即调用 confirmUpdateNotificationSettings**("
                  + "   confirm_token=\"" + token + "\")。"
                  + "   这一步是**真正写 Redis** 的唯一入口，10 分钟内必须调用否则会过期。\n"
                  + "4) 拿到 confirm 的返回（status=UPDATED）后才回复用户'已保存/已关闭/已开启'。\n"
                  + "\n"
                  + "【硬性禁止 - 不要违反】\n"
                  + "❌ 禁止在没调用 confirmUpdateNotificationSettings 之前告诉用户'已修改/已关闭/已开启/已保存'。"
                  + "   哪怕用户口头同意了，也必须等 confirm 返回 status=UPDATED 才算成功。\n"
                  + "❌ 禁止把这次返回当作最终结果。本次返回 status 是 CONFIRM_REQUIRED，"
                  + "   不是 UPDATED，写入动作**还没发生**。\n"
                  + "❌ 禁止跳过 confirm 直接说'好的已经帮您关了'，这是幻觉，后端根本没收到指令。\n"
                  + "❌ 禁止用户没确认就调 confirm；如果不确定，**再次询问用户**。"
        );
    }

    /**
     * 修改通知设置（**二次确认** 第二步）
     */
    @Tool("修改通知设置（第二步：用户已确认后真正写入）。必须传入 prepareUpdateNotificationSettings 返回的 confirm_token。")
    public Map<String, Object> confirmUpdateNotificationSettings(
            @P("prepareUpdateNotificationSettings 返回的确认 token") String confirmToken) {

        Long userId = AiUserContext.requireUserId();
        log.info("[AI-TOOL] confirmUpdateNotificationSettings userId={}, token={}", userId, confirmToken);

        if (confirmToken == null || confirmToken.isBlank()) {
            return Map.of("error", "confirm_token 不能为空");
        }
        PendingSettings pending = pendingMap.remove(confirmToken);
        if (pending == null) {
            return Map.of("error", "确认凭证无效或已过期，请重新发起修改");
        }
        if (!pending.userId.equals(userId.intValue())) {
            return Map.of("error", "确认凭证归属错误");
        }
        if (System.currentTimeMillis() - pending.createdAtMs > 10 * 60 * 1000L) {
            return Map.of("error", "确认凭证已过期，请重新发起修改");
        }

        NotificationSettingsDTO dto = new NotificationSettingsDTO();
        dto.setEnablePush(pending.enablePush);
        dto.setPhotoUpload(pending.photoUpload);
        dto.setAnniversary(pending.anniversary);
        dto.setEmail(pending.email);
        dto.setSystem(pending.system);

        try {
            Result<NotificationSettingsVO> r = userService.updateNotificationSettings(userId.intValue(), dto);
            if (r == null || !r.isSuccess() || r.getData() == null) {
                return Map.of("error", r == null ? "null" : r.getMessage());
            }
            Map<String, Object> resp = toMap(r.getData());
            resp.put("status", "UPDATED");
            return resp;
        } catch (Exception e) {
            log.error("[AI-TOOL] confirmUpdateNotificationSettings 失败", e);
            return Map.of("error", "保存失败：" + e.getMessage());
        }
    }

    // ==================== 内部 ====================

    private Map<String, Object> toMap(NotificationSettingsVO vo) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("enablePush", vo.getEnablePush());
        m.put("photoUpload", vo.getPhotoUpload());
        m.put("anniversary", vo.getAnniversary());
        m.put("email", vo.getEmail());
        m.put("system", vo.getSystem());
        return m;
    }

    /**
     * 构造"原值 → 新值"行；若 newVal 为 null 表示未改，给出"(不变)"标记
     */
    private Map<String, Object> diffLine(String field, Boolean oldVal, Boolean newVal) {
        Map<String, Object> line = new HashMap<>();
        line.put("field", field);
        line.put("old", oldVal);
        if (newVal == null) {
            line.put("new", null);
            line.put("changed", false);
            line.put("note", "(不变)");
        } else {
            line.put("new", newVal);
            line.put("changed", !Boolean.valueOf(Boolean.TRUE.equals(oldVal)).equals(Boolean.valueOf(newVal)));
        }
        return line;
    }

    /** 待执行的设置修改（含过期机制） */
    private record PendingSettings(
            Integer userId,
            Boolean enablePush,
            Boolean photoUpload,
            Boolean anniversary,
            Boolean email,
            Boolean system,
            long createdAtMs
    ) {}
}