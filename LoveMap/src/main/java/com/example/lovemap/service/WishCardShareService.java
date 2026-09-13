package com.example.lovemap.service;

import com.example.lovemap.chat.ChatPresenceRegistry;
import com.example.lovemap.chat.ChatSessionRegistry;
import com.example.lovemap.chat.WsChatMessage;
import com.example.lovemap.common.BusinessException;
import com.example.lovemap.common.ResultCode;
import com.example.lovemap.common.constant.WishlistConstant;
import com.example.lovemap.mapper.ChatMessageMapper;
import com.example.lovemap.mapper.UserMapper;
import com.example.lovemap.mapper.WishlistItemMapper;
import com.example.lovemap.model.entity.ChatMessage;
import com.example.lovemap.model.entity.User;
import com.example.lovemap.model.entity.WishlistItem;
import com.example.lovemap.utils.storage.FileStorage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 把"心愿记录卡片"作为一张卡片消息发送给伴侣。
 * <p>
 * 复用 chat_message 表（V8 扩展），msg_type = 6：
 *   image_url  = 卡片图（前端导出的 PNG，上传到文件存储）
 *   extra_json = {wishId, title, category, achievedAt, note} 摘要
 *   content    = 一句话提示
 * <p>
 * 推送链路与 {@link com.example.lovemap.makeover.service.MakeoverShareService} 保持一致：
 * 落库 → SSE 未读 → 对方在聊天页则立即已读 → WS 推 CHAT 帧。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WishCardShareService {

    private final WishlistItemMapper wishlistItemMapper;
    private final UserMapper userMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final FileStorage fileStorage;
    private final SseService sseService;
    private final ChatSessionRegistry chatSessionRegistry;
    private final ChatPresenceRegistry chatPresenceRegistry;
    private final ObjectMapper objectMapper;

    /**
     * 发送记录卡片给伴侣
     *
     * @param userId  当前用户
     * @param itemId  心愿项 ID
     * @param image   卡片图（PNG）
     * @param note    卡片上展示的回忆笔记（可为空，空则回退到心愿自身的达成笔记）
     * @return 新建的聊天消息 ID
     */
    public Long share(Integer userId, Long itemId, MultipartFile image, String note) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }
        WishlistItem item = wishlistItemMapper.selectByIdAndGroupOrUser(itemId, user.getGroupId(), user.getId());
        if (item == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "心愿项不存在");
        }
        if (item.getStatus() == null || item.getStatus() != WishlistConstant.STATUS_COMPLETED) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "心愿达成后才能发送卡片");
        }
        if (image == null || image.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "卡片图片为空");
        }
        if (user.getPartnerId() == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "请先绑定伴侣");
        }
        Integer receiverId = Math.toIntExact(user.getPartnerId());

        String imageUrl = uploadCardImage(itemId, image);

        ChatMessage msg = new ChatMessage();
        msg.setSenderId(userId);
        msg.setReceiverId(receiverId);
        msg.setContent(buildContent(item));
        msg.setImageUrl(imageUrl);
        msg.setMsgType((int) WishlistConstant.MSG_TYPE_WISH_CARD);
        msg.setExtraJson(buildExtraJson(item, note));
        msg.setIsRead(0);
        msg.setCreatedAt(LocalDateTime.now());

        try {
            chatMessageMapper.insert(msg);
        } catch (Exception e) {
            log.error("[WishCard-Share] 卡片消息落库失败 userId={}, itemId={}", userId, itemId, e);
            throw new BusinessException(ResultCode.INTERNAL_SERVER_ERROR, "发送失败，请稍后重试");
        }
        log.info("[WishCard-Share] 卡片消息已创建 id={}, userId={}, itemId={}", msg.getId(), userId, itemId);

        pushNotifications(userId, receiverId, msg);
        return msg.getId();
    }

    private String uploadCardImage(Long itemId, MultipartFile image) {
        String objectKey = WishlistConstant.OSS_CARD_PREFIX + itemId + "_" + System.currentTimeMillis() + ".png";
        try {
            return fileStorage.uploadBytes(image.getBytes(), objectKey, "image/png");
        } catch (Exception e) {
            log.error("[WishCard-Share] 卡片图上传失败 itemId={}", itemId, e);
            throw new BusinessException(ResultCode.INTERNAL_SERVER_ERROR, "卡片发送失败，请稍后重试");
        }
    }

    /**
     * SSE 未读 / 立即已读回执 / WS 推 CHAT 帧
     */
    private void pushNotifications(Integer senderId, Integer receiverId, ChatMessage msg) {
        try {
            long unreadCount = chatMessageMapper.countUnreadFrom(senderId, receiverId);
            Map<String, Object> unreadEvent = new LinkedHashMap<>();
            unreadEvent.put("count", unreadCount);
            unreadEvent.put("partnerId", senderId);
            sseService.sendEvent(receiverId, "chat-unread-count", unreadEvent);
        } catch (Exception e) {
            log.warn("[WishCard-Share] 推送未读 SSE 失败 receiverId={}", receiverId, e);
        }

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
                log.warn("[WishCard-Share] 标记已读失败 msgId={}", msg.getId(), e);
            }
        }

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
            log.error("[WishCard-Share] WS 帧序列化失败 msgId={}", msg.getId(), e);
            return;
        }
        try {
            chatSessionRegistry.sendTo(senderId, json);
            chatSessionRegistry.sendTo(receiverId, json);
        } catch (Exception e) {
            log.warn("[WishCard-Share] WS 推送失败", e);
        }
    }

    private String buildContent(WishlistItem item) {
        String title = item.getTitle() == null ? "心愿" : item.getTitle();
        return "我完成了心愿「" + title + "」，快来看看～";
    }

    /**
     * 构造 extra_json 摘要。笔记先按字符数裁剪，保证 JSON 始终合法（不会被中途截断）。
     */
    private String buildExtraJson(WishlistItem item, String note) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("wishId", item.getId());
        root.put("title", item.getTitle());
        if (item.getCategory() != null) {
            root.put("category", item.getCategory());
        }
        if (item.getIcon() != null) {
            root.put("icon", item.getIcon());
        }
        if (item.getAchievedAt() != null) {
            root.put("achievedAt", item.getAchievedAt().toString());
        }

        String text = (note != null && !note.isBlank()) ? note.trim() : item.getAchievedNote();
        if (text != null && !text.isBlank()) {
            if (text.length() > WishlistConstant.CARD_NOTE_MAX) {
                text = text.substring(0, WishlistConstant.CARD_NOTE_MAX) + "…";
            }
            root.put("note", text);
        }
        return root.toString();
    }
}
