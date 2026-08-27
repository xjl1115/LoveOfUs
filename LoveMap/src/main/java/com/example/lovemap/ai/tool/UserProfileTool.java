package com.example.lovemap.ai.tool;

import com.example.lovemap.ai.context.AiUserContext;
import com.example.lovemap.common.constant.UserConstant;
import com.example.lovemap.mapper.UserMapper;
import com.example.lovemap.model.entity.User;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * 用户资料 AI 工具
 * <p>
 * 提供 4 个工具：
 * <ul>
 *   <li>getMyProfile —— 读取昵称、手机号（脱敏）、邮箱</li>
 *   <li>prepareUpdateNickname / confirmUpdateNickname —— 修改昵称（二次确认）</li>
 *   <li>prepareUpdatePhone    / confirmUpdatePhone    —— 修改手机号（二次确认）</li>
 *   <li>prepareUpdateEmail    / confirmUpdateEmail    —— 修改邮箱（二次确认）</li>
 * </ul>
 * <p>
 * 二次确认机制：prepare 阶段只校验 + 返回 confirm_token（待写入内容在内存中暂存 1 小时）；
 * confirm 阶段校验 token 归属后才真正写 DB。任何直接跳过 confirm 的尝试都会被 token 校验拦截。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserProfileTool {

    private final UserMapper userMapper;
    private final StringRedisTemplate redisTemplate;

    /** 手机号正则：以 1 开头的 11 位数字 */
    private static final Pattern PHONE_PATTERN = Pattern.compile("^1\\d{10}$");
    /** 邮箱正则：宽松校验，包含 @ 与域名即可 */
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    /** 待执行的资料修改：token -> PendingUpdate。仅在单次会话内有效，1 小时过期。 */
    private final Map<String, PendingUpdate> pendingMap = new ConcurrentHashMap<>();

    // ==================== 查询 ====================

    /**
     * 获取当前用户的昵称、手机号（脱敏）、邮箱
     */
    @Tool("获取当前登录用户的昵称、手机号（已脱敏）和邮箱。")
    public Map<String, Object> getMyProfile() {
        Long userId = AiUserContext.requireUserId();
        log.info("[AI-TOOL] getMyProfile userId={}", userId);
        try {
            User user = userMapper.selectById(userId.intValue());
            if (user == null) {
                return Map.of("error", "用户不存在");
            }
            Map<String, Object> result = new HashMap<>();
            result.put("userId", user.getId());
            result.put("nickname", user.getNickname());
            result.put("phone", maskPhone(user.getPhone()));
            result.put("phoneMasked", true);
            result.put("email", user.getEmail());
            return result;
        } catch (Exception e) {
            log.error("[AI-TOOL] getMyProfile 失败", e);
            return Map.of("error", "查询失败：" + e.getMessage());
        }
    }

    // ==================== 昵称 ====================

    /**
     * 修改昵称（**二次确认** 第一步）
     */
    @Tool("修改用户昵称（第一步：返回确认 token，不直接写入）。需要用户在前端二次确认。")
    public Map<String, Object> prepareUpdateNickname(
            @P("新昵称，1-11 个字符") String newNickname) {

        Long userId = AiUserContext.requireUserId();
        log.info("[AI-TOOL] prepareUpdateNickname userId={}, newNickname='{}'", userId, newNickname);

        if (newNickname == null || newNickname.isBlank()) {
            return Map.of("error", "新昵称不能为空");
        }
        String trimmed = newNickname.trim();
        if (trimmed.length() < 1 || trimmed.length() > 11) {
            return Map.of("error", "昵称长度需在 1-11 个字符之间");
        }

        User user = userMapper.selectById(userId.intValue());
        if (user == null) {
            return Map.of("error", "用户不存在");
        }
        if (trimmed.equals(user.getNickname())) {
            return Map.of("error", "新昵称与当前昵称相同，无需修改");
        }

        return stashAndPreview(user, "nickname", trimmed, null, null);
    }

    /**
     * 修改昵称（**二次确认** 第二步）
     */
    @Tool("修改用户昵称（第二步：用户已确认后真正写入）。必须传入 prepareUpdateNickname 返回的 confirm_token。")
    public Map<String, Object> confirmUpdateNickname(
            @P("prepareUpdateNickname 返回的确认 token") String confirmToken) {
        return executeUpdate(confirmToken, "nickname");
    }

    // ==================== 手机号 ====================

    /**
     * 修改手机号（**二次确认** 第一步）
     */
    @Tool("修改用户手机号（第一步：返回确认 token，不直接写入）。需要用户在前端二次确认。")
    public Map<String, Object> prepareUpdatePhone(
            @P("新手机号，必须是 11 位以 1 开头的数字") String newPhone) {

        Long userId = AiUserContext.requireUserId();
        log.info("[AI-TOOL] prepareUpdatePhone userId={}, newPhone='{}'", userId, newPhone);

        if (newPhone == null || !PHONE_PATTERN.matcher(newPhone).matches()) {
            return Map.of("error", "手机号格式错误，必须是 11 位以 1 开头的数字");
        }

        User user = userMapper.selectById(userId.intValue());
        if (user == null) {
            return Map.of("error", "用户不存在");
        }
        if (newPhone.equals(user.getPhone())) {
            return Map.of("error", "新手机号与当前手机号相同，无需修改");
        }
        // 唯一性预校验：避免确认后才发现被占用
        if (userMapper.findByPhone(newPhone) != null) {
            return Map.of("error", "该手机号已被其他账号绑定，请换一个");
        }

        return stashAndPreview(user, "phone", null, newPhone, null);
    }

    /**
     * 修改手机号（**二次确认** 第二步）
     */
    @Tool("修改用户手机号（第二步：用户已确认后真正写入）。必须传入 prepareUpdatePhone 返回的 confirm_token。")
    public Map<String, Object> confirmUpdatePhone(
            @P("prepareUpdatePhone 返回的确认 token") String confirmToken) {
        return executeUpdate(confirmToken, "phone");
    }

    // ==================== 邮箱 ====================

    /**
     * 修改邮箱（**二次确认** 第一步）
     */
    @Tool("修改用户邮箱（第一步：返回确认 token，不直接写入）。需要用户在前端二次确认。")
    public Map<String, Object> prepareUpdateEmail(
            @P("新邮箱地址，例如：name@example.com") String newEmail) {

        Long userId = AiUserContext.requireUserId();
        log.info("[AI-TOOL] prepareUpdateEmail userId={}, newEmail='{}'", userId, newEmail);

        if (newEmail == null || !EMAIL_PATTERN.matcher(newEmail).matches()) {
            return Map.of("error", "邮箱格式错误");
        }
        String lower = newEmail.toLowerCase();

        User user = userMapper.selectById(userId.intValue());
        if (user == null) {
            return Map.of("error", "用户不存在");
        }
        if (lower.equalsIgnoreCase(user.getEmail())) {
            return Map.of("error", "新邮箱与当前邮箱相同，无需修改");
        }
        if (userMapper.findByEmail(lower) != null) {
            return Map.of("error", "该邮箱已被其他账号绑定，请换一个");
        }

        return stashAndPreview(user, "email", null, null, lower);
    }

    /**
     * 修改邮箱（**二次确认** 第二步）
     */
    @Tool("修改用户邮箱（第二步：用户已确认后真正写入）。必须传入 prepareUpdateEmail 返回的 confirm_token。")
    public Map<String, Object> confirmUpdateEmail(
            @P("prepareUpdateEmail 返回的确认 token") String confirmToken) {
        return executeUpdate(confirmToken, "email");
    }

    // ==================== 内部实现 ====================

    /**
     * 生成 token + 暂存待修改内容 + 返回预览
     */
    private Map<String, Object> stashAndPreview(User user, String field, String nickname, String phone, String email) {
        String token = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        pendingMap.put(token, new PendingUpdate(
                user.getId().intValue(),
                field,
                nickname,
                phone,
                email,
                System.currentTimeMillis()
        ));
        log.info("[AI-TOOL] ===== prepare update DONE userId={}, field={}, token='{}', newValue='{}' =====",
                user.getId(), field, token,
                newValueOf(field, nickname, phone, email));
        // 1 小时过期清理
        new Timer().schedule(new TimerTask() {
            @Override public void run() { pendingMap.remove(token); }
        }, 60 * 60 * 1000L);

        Map<String, Object> preview = new HashMap<>();
        preview.put("field", field);
        preview.put("oldValue", oldValueOf(user, field));
        preview.put("newValue", newValueOf(field, nickname, phone, email));
        if ("phone".equals(field)) {
            preview.put("newValueMasked", maskPhone((String) preview.get("newValue")));
        }

        Map<String, Object> resp = new HashMap<>();
        resp.put("status", "CONFIRM_REQUIRED");
        resp.put("confirm_token", token);
        resp.put("preview", preview);
        // 强化提示：用更强硬的措辞防止 qwen3.7-flash 类小模型直接当成"修改成功"
        resp.put("hint", "⚠️ 数据库尚未修改！请你向用户复述以上变更（X 改为 Y）并明确请求用户确认。"
                + "用户明确说'确认/同意/好的'之后，你**必须**立即调用工具 confirmUpdate"
                + capitalize(field) + "(confirm_token=\"" + token + "\") 真正写入数据库。"
                + "在用户确认前**禁止**告诉用户'已修改/完成/成功'。"
                + "如果用户没有明确确认或表达拒绝，**禁止**调用 confirm* 工具。");
        return resp;
    }

    /**
     * 真正写入 DB（confirm 阶段调用）
     */
    private Map<String, Object> executeUpdate(String confirmToken, String expectedField) {
        Long userId = AiUserContext.requireUserId();
        log.info("[AI-TOOL] ===== confirm update START userId={}, token='{}', expectedField='{}' =====",
                userId, confirmToken, expectedField);

        if (confirmToken == null || confirmToken.isBlank()) {
            log.warn("[AI-TOOL] confirm token 为空");
            return Map.of("error", "confirm_token 不能为空");
        }
        PendingUpdate pending = pendingMap.remove(confirmToken);
        if (pending == null) {
            log.warn("[AI-TOOL] confirm token 未找到或已过期 token='{}' currentMapSize={}",
                    confirmToken, pendingMap.size());
            return Map.of("error", "确认凭证无效或已过期，请重新发起修改");
        }
        log.info("[AI-TOOL] 找到 pending: userId={}, field={}, nickname='{}', phone='{}', email='{}'",
                pending.userId, pending.field, pending.nickname, pending.phone, pending.email);
        if (!pending.userId.equals(userId.intValue())) {
            log.warn("[AI-TOOL] token 归属错误: tokenUserId={} currentUserId={}", pending.userId, userId);
            return Map.of("error", "确认凭证归属错误");
        }
        if (!expectedField.equals(pending.field)) {
            log.warn("[AI-TOOL] token 字段不匹配: tokenField={} expectedField={}", pending.field, expectedField);
            return Map.of("error", "确认凭证类型不匹配（期望：" + expectedField + "）");
        }
        // 10 分钟过期（防止 token 长期滞留被误用；远小于 1 小时清理窗口）
        if (System.currentTimeMillis() - pending.createdAtMs > 10 * 60 * 1000L) {
            log.warn("[AI-TOOL] token 已过期 token='{}' ageMs={}", confirmToken,
                    System.currentTimeMillis() - pending.createdAtMs);
            return Map.of("error", "确认凭证已过期，请重新发起修改");
        }

        User user = userMapper.selectById(pending.userId);
        if (user == null) {
            log.warn("[AI-TOOL] 用户不存在 userId={}", pending.userId);
            return Map.of("error", "用户不存在");
        }
        String oldValue = oldValueOf(user, pending.field);
        switch (pending.field) {
            case "nickname" -> user.setNickname(pending.nickname);
            case "phone" -> user.setPhone(pending.phone);
            case "email" -> user.setEmail(pending.email);
            default -> {
                return Map.of("error", "不支持的字段：" + pending.field);
            }
        }
        log.info("[AI-TOOL] 准备更新 userId={}, field={}, oldValue='{}', newValue='{}'",
                pending.userId, pending.field, oldValue, newValueOf(pending.field, pending.nickname, pending.phone, pending.email));

        try {
            int n = userMapper.updateUser(user);
            log.info("[AI-TOOL] updateUser 返回影响行数 n={} userId={} field={}",
                    n, pending.userId, pending.field);
            if (n <= 0) {
                log.warn("[AI-TOOL] 更新失败，影响行数为 0 userId={} field={}",
                        pending.userId, pending.field);
                return Map.of("error", "更新失败，影响行数为 0");
            }
        } catch (DuplicateKeyException e) {
            String msg = e.getMessage();
            if (msg != null && msg.contains("uk_phone")) {
                return Map.of("error", "该手机号已被其他账号绑定");
            }
            if (msg != null && msg.contains("uk_email")) {
                return Map.of("error", "该邮箱已被其他账号绑定");
            }
            log.error("[AI-TOOL] confirmUpdate {} 唯一索引冲突", pending.field, e);
            return Map.of("error", "数据库唯一索引冲突");
        } catch (Exception e) {
            log.error("[AI-TOOL] confirmUpdate {} 失败", pending.field, e);
            return Map.of("error", "更新失败：" + e.getMessage());
        }

        // 清除缓存（参考 UserServiceImpl.updateUserInfo 的清理策略）
        try {
            redisTemplate.delete(UserConstant.USER_INFO + pending.userId);
        } catch (Exception e) {
            log.warn("[AI-TOOL] 清除用户缓存失败 userId={}", pending.userId, e);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("status", "UPDATED");
        result.put("field", pending.field);
        result.put("oldValue", oldValue);
        result.put("newValue", newValueOf(pending.field, pending.nickname, pending.phone, pending.email));
        log.info("[AI-TOOL] ===== confirm update DONE userId={}, field={}, newValue='{}' =====",
                pending.userId, pending.field, result.get("newValue"));
        return result;
    }

    private String oldValueOf(User user, String field) {
        return switch (field) {
            case "nickname" -> user.getNickname();
            case "phone" -> maskPhone(user.getPhone());
            case "email" -> user.getEmail();
            default -> null;
        };
    }

    private String newValueOf(String field, String nickname, String phone, String email) {
        return switch (field) {
            case "nickname" -> nickname;
            case "phone" -> phone;
            case "email" -> email;
            default -> null;
        };
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** 手机号脱敏：138****8000 */
    private static String maskPhone(String phone) {
        if (!StringUtils.hasText(phone) || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    /** 待修改的资料（含过期机制） */
    private record PendingUpdate(
            Integer userId,
            String field,
            String nickname,
            String phone,
            String email,
            long createdAtMs
    ) {}
}