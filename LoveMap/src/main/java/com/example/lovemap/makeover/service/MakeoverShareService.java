package com.example.lovemap.makeover.service;

import com.example.lovemap.chat.ChatPresenceRegistry;
import com.example.lovemap.chat.ChatSessionRegistry;
import com.example.lovemap.chat.WsChatMessage;
import com.example.lovemap.common.BusinessException;
import com.example.lovemap.common.ResultCode;
import com.example.lovemap.mapper.ChatMessageMapper;
import com.example.lovemap.mapper.UserMapper;
import com.example.lovemap.makeover.constant.MakeoverConstant;
import com.example.lovemap.makeover.mapper.MakeoverRecordMapper;
import com.example.lovemap.model.entity.ChatMessage;
import com.example.lovemap.model.entity.MakeoverRecord;
import com.example.lovemap.model.entity.User;
import com.example.lovemap.service.SseService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 把"AI 化妆建议"作为一张卡片消息发送给伴侣。
 * <p>
 * 复用 chat_message 表（V8 扩展），msg_type = 5，
 *   image_url  = afterUrl （卡片主图）
 *   extra_json = {faceFeatures, suggestions} 摘要
 *   content    = 一句话提示，如"我刚试了 AI 化妆建议，快来看看"
 * <p>
 * 触发推送：
 *   1. 保存到 chat_message
 *   2. 推 SSE 未读数给对方（chat-unread-count）
 *   3. 如果对方 WS 在线且停留在聊天页 → 立即标记已读 + SSE 推已读回执
 *   4. WS 在线者直接推送 CHAT 帧（带 imageUrl + extraJson），前端即时渲染卡片
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MakeoverShareService {

    private final MakeoverRecordMapper recordMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final UserMapper userMapper;
    private final SseService sseService;
    private final ChatSessionRegistry chatSessionRegistry;
    private final ChatPresenceRegistry chatPresenceRegistry;
    private final ObjectMapper objectMapper;

    /**
     * 分享妆造卡片
     *
     * @param userId   当前用户
     * @param recordId AI 化妆建议记录 ID
     * @return 新建的聊天消息 ID
     */
    public Long share(Integer userId, Long recordId) {
        // 1. 校验记录
        MakeoverRecord record = recordMapper.selectById(recordId);
        if (record == null || !record.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "记录不存在");
        }
        if (record.getStatus() != MakeoverConstant.STATUS_DONE) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "仅已完成的建议可分享");
        }
        if (record.getAfterUrl() == null || record.getAfterUrl().isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "改造图未生成，无法分享");
        }

        // 2. 校验伴侣绑定
        User user = userMapper.selectById(userId);
        if (user == null || user.getPartnerId() == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "请先绑定伴侣");
        }
        Integer receiverId = Math.toIntExact(user.getPartnerId());

        // 3. 构造 extraJson（faceFeatures + suggestions 摘要）
        String extraJson = buildExtraJson(record);

        // 4. 构造 ChatMessage
        ChatMessage msg = new ChatMessage();
        msg.setSenderId(userId);
        msg.setReceiverId(receiverId);
        msg.setContent(buildShareContent(record.getSceneCode()));
        msg.setImageUrl(record.getAfterUrl());
        msg.setMsgType((int) MakeoverConstant.MSG_TYPE_MAKEOVER_CARD);
        msg.setExtraJson(extraJson);
        msg.setIsRead(0);
        msg.setCreatedAt(LocalDateTime.now());

        try {
            chatMessageMapper.insert(msg);
        } catch (Exception e) {
            log.error("[Makeover-Share] 卡片消息落库失败 userId={}, recordId={}", userId, recordId, e);
            throw new BusinessException(ResultCode.INTERNAL_SERVER_ERROR, "分享失败，请稍后重试");
        }
        log.info("[Makeover-Share] 卡片消息已创建 id={}, userId={}, recordId={}",
                msg.getId(), userId, recordId);

        // 5. 推送未读 SSE / WS
        pushNotifications(userId, receiverId, msg);
        return msg.getId();
    }

    /**
     * 推送：SSE 未读 / 立即已读回执 / WS 推 CHAT 帧
     */
    private void pushNotifications(Integer senderId, Integer receiverId, ChatMessage msg) {
        // 5a. 推未读数 SSE 给接收方（即使对方不在聊天页，右上角未读角标也能立刻更新）
        try {
            long unreadCount = chatMessageMapper.countUnreadFrom(senderId, receiverId);
            Map<String, Object> unreadEvent = new LinkedHashMap<>();
            unreadEvent.put("count", unreadCount);
            unreadEvent.put("partnerId", senderId);
            sseService.sendEvent(receiverId, "chat-unread-count", unreadEvent);
        } catch (Exception e) {
            log.warn("[Makeover-Share] 推送未读 SSE 失败 receiverId={}", receiverId, e);
        }

        // 5b. 接收方停留在聊天页 → 立即标记已读 + SSE 推已读回执给发送方
        if (chatPresenceRegistry.isInChatWith(receiverId, senderId)) {
            try {
                chatMessageMapper.markRead(msg.getId(), receiverId, LocalDateTime.now());
                msg.setIsRead(1);
                msg.setReadAt(LocalDateTime.now());
                Map<String, Object> readEvent = new LinkedHashMap<>();
                readEvent.put("lastReadId", msg.getId());
                readEvent.put("partnerId", senderId);
                readEvent.put("readAt", msg.getReadAt().toString());
                sseService.sendEvent(senderId, "chat-read", readEvent);
            } catch (Exception e) {
                log.warn("[Makeover-Share] 标记已读失败 msgId={}", msg.getId(), e);
            }
        }

        // 5c. WS 在线者直接推 CHAT 帧（不依赖对方是否在聊天页，只要 WS 在就推）
        WsChatMessage wsMsg = new WsChatMessage();
        wsMsg.setType("CHAT");
        wsMsg.setId(msg.getId());
        wsMsg.setSenderId(msg.getSenderId());
        wsMsg.setReceiverId(msg.getReceiverId());
        wsMsg.setContent(msg.getContent());
        wsMsg.setImageUrl(msg.getImageUrl());
        wsMsg.setMsgType(msg.getMsgType());
        wsMsg.setExtraJson(msg.getExtraJson());
        wsMsg.setIsRead(msg.getIsRead());
        wsMsg.setCreatedAt(msg.getCreatedAt().toString());

        String json;
        try {
            json = objectMapper.writeValueAsString(wsMsg);
        } catch (Exception e) {
            log.error("[Makeover-Share] WS 帧序列化失败 msgId={}", msg.getId(), e);
            return;
        }
        // 给发送方（自身）和接收方都推一份；发送方通常用 WS 回执或下次进 /chat 时拉到
        try {
            chatSessionRegistry.sendTo(senderId, json);
            chatSessionRegistry.sendTo(receiverId, json);
        } catch (Exception e) {
            log.warn("[Makeover-Share] WS 推送失败", e);
        }
    }

    /**
     * 把 faceFeatures + suggestions 拼成 extraJson（VARCHAR 2000 字符上限）。
     * 体积过大时压缩 tips 字段，避免被 MySQL 截断。
     */
    private String buildExtraJson(MakeoverRecord record) {
        ObjectNode root = objectMapper.createObjectNode();
        try {
            String faceRaw = record.getFaceFeatures();
            if (faceRaw != null && !faceRaw.isBlank()) {
                Map<String, Object> faceMap = objectMapper.readValue(faceRaw, new TypeReference<>() {});
                root.set("faceFeatures", objectMapper.valueToTree(faceMap));
            }
            String suggRaw = record.getSuggestions();
            if (suggRaw != null && !suggRaw.isBlank()) {
                Map<String, Object> suggMap = objectMapper.readValue(suggRaw, new TypeReference<>() {});
                // 截短 tips，避免超 2000 字符
                if (suggMap.get("tips") instanceof java.util.List<?> tips) {
                    if (tips.size() > 3) {
                        suggMap.put("tips", tips.subList(0, 3));
                    }
                }
                root.set("suggestions", objectMapper.valueToTree(suggMap));
            }
            root.put("recordId", record.getId());
        } catch (Exception e) {
            log.warn("[Makeover-Share] extraJson 构造失败 recordId={}, err={}", record.getId(), e.getMessage());
        }
        String json = root.toString();
        if (json.length() > MakeoverConstant.CARD_EXTRA_JSON_MAX) {
            log.warn("[Makeover-Share] extraJson 超 {} 字符 ({}), 已截断 recordId={}",
                    MakeoverConstant.CARD_EXTRA_JSON_MAX, json.length(), record.getId());
            json = json.substring(0, MakeoverConstant.CARD_EXTRA_JSON_MAX);
        }
        return json;
    }

    private String sceneName(String code) {
        if (code == null) return "其他";
        return MakeoverConstant.SCENE_NAME.getOrDefault(code, code);
    }

    /**
     * 根据场景拼出"分享给伴侣"的开场白。
     * 选不同场景时语气不同，让伴侣一眼看到这次 AI 妆造是为哪个场合准备的。
     */
    private String buildShareContent(String sceneCode) {
        String template = MakeoverConstant.SHARE_PROMPT.get(sceneCode);
        if (template == null) template = MakeoverConstant.SHARE_PROMPT_FALLBACK;
        return template.replace("{scene}", sceneName(sceneCode));
    }
}