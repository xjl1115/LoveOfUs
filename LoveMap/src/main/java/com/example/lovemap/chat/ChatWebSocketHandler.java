package com.example.lovemap.chat;

import com.example.lovemap.common.ResultCode;
import com.example.lovemap.mapper.ChatMessageMapper;
import com.example.lovemap.mapper.UserMapper;
import com.example.lovemap.model.entity.ChatMessage;
import com.example.lovemap.model.entity.User;
import com.example.lovemap.service.SseService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 聊天 WebSocket 处理器
 * <p>
 * 处理协议：
 *   - CHAT   保存到 DB + 推送给对方
 *   - TYPING 直接转发给对方（不持久化）
 *   - READ   批量标记对方发来的未读消息为已读 + 推已读回执给对方
 *   - PING   回复 PONG
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final ChatSessionRegistry sessionRegistry;
    private final ChatMessageMapper chatMessageMapper;
    private final UserMapper userMapper;
    private final ChatPresenceRegistry chatPresenceRegistry;
    private final SseService sseService;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Integer userId = (Integer) session.getAttributes().get(ChatHandshakeInterceptor.ATTR_USER_ID);
        if (userId == null) {
            log.warn("聊天 WS 连接建立但未携带 userId，强制关闭");
            try {
                session.close(CloseStatus.POLICY_VIOLATION);
            } catch (Exception ignored) {
            }
            return;
        }
        sessionRegistry.register(userId, session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Integer userId = (Integer) session.getAttributes().get(ChatHandshakeInterceptor.ATTR_USER_ID);
        if (userId != null) {
            sessionRegistry.unregister(userId, session);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Integer userId = (Integer) session.getAttributes().get(ChatHandshakeInterceptor.ATTR_USER_ID);
        if (userId == null) {
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }

        WsChatMessage in;
        try {
            in = objectMapper.readValue(message.getPayload(), WsChatMessage.class);
        } catch (Exception e) {
            log.warn("聊天 WS 消息解析失败, userId: {}, payload: {}", userId, message.getPayload(), e);
            sendError(session, "消息格式错误");
            return;
        }

        if (in.getType() == null) {
            sendError(session, "消息类型不能为空");
            return;
        }

        switch (in.getType().toUpperCase()) {
            case "CHAT" -> handleChat(session, userId, in);
            case "TYPING" -> handleTyping(session, userId, in);
            case "READ" -> handleRead(session, userId, in);
            case "DELETE" -> handleDelete(session, userId, in);
            case "RECALL" -> handleRecall(session, userId, in);
            case "PING" -> sendText(session, Map.of("type", "PONG"));
            default -> sendError(session, "未知消息类型: " + in.getType());
        }
    }

    private void handleChat(WebSocketSession session, Integer userId, WsChatMessage in) {
        String content = in.getContent();
        if (content == null || content.isBlank()) {
            sendError(session, "消息内容不能为空");
            return;
        }
        if (content.length() > 2000) {
            sendError(session, "消息内容超过2000字符");
            return;
        }

        User user = userMapper.selectById(userId);
        if (user == null || user.getPartnerId() == null) {
            sendError(session, "请先绑定伴侣");
            return;
        }

        ChatMessage entity = new ChatMessage();
        entity.setSenderId(userId);
        entity.setReceiverId(user.getPartnerId().intValue());
        entity.setContent(content);
        entity.setMsgType(in.getMsgType() == null ? 1 : in.getMsgType());
        entity.setIsRead(0);
        entity.setCreatedAt(LocalDateTime.now());

        try {
            chatMessageMapper.insert(entity);
        } catch (Exception e) {
            log.error("聊天消息保存失败, userId: {}", userId, e);
            sendError(session, "消息发送失败，请稍后重试");
            return;
        }

        // 接收方当前停留在聊天页：直接标记为已读，并通过 SSE 实时推送已读回执给发送方
        Integer receiverId = Math.toIntExact(entity.getReceiverId());
        if (chatPresenceRegistry.isInChatWith(receiverId, userId)) {
            chatMessageMapper.markRead(entity.getId(), receiverId, LocalDateTime.now());
            entity.setIsRead(1);
            entity.setReadAt(LocalDateTime.now());

            // SSE 推送已读回执给发送方（SSE 通道独立于 WS，即使对方 WS 暂时断开也能收到）
            Map<String, Object> readEvent = new LinkedHashMap<>();
            readEvent.put("lastReadId", entity.getId());
            readEvent.put("partnerId", userId);
            readEvent.put("readAt", entity.getReadAt().toString());
            sseService.sendEvent(userId, "chat-read", readEvent);
        }

        // 回执发送者（含 server id / createdAt）
        WsChatMessage ack = new WsChatMessage();
        ack.setType("CHAT");
        ack.setId(entity.getId());
        ack.setClientMsgId(in.getClientMsgId());
        ack.setSenderId(entity.getSenderId());
        ack.setReceiverId(entity.getReceiverId());
        ack.setContent(entity.getContent());
        ack.setMsgType(entity.getMsgType());
        ack.setIsRead(entity.getIsRead());
        ack.setCreatedAt(entity.getCreatedAt().toString());
        sendTo(session, userId, ack);

        // 推送对方
        sendTo(session, receiverId, ack);
    }

    private void handleTyping(WebSocketSession session, Integer userId, WsChatMessage in) {
        User user = userMapper.selectById(userId);
        if (user == null || user.getPartnerId() == null) {
            return;
        }
        WsChatMessage out = new WsChatMessage();
        out.setType("TYPING");
        out.setSenderId(userId);
        out.setReceiverId(Math.toIntExact(user.getPartnerId()));
        sendTo(session, Math.toIntExact(user.getPartnerId()), out);
    }

    private void handleRead(WebSocketSession session, Integer userId, WsChatMessage in) {
        User user = userMapper.selectById(userId);
        if (user == null || user.getPartnerId() == null) {
            return;
        }
        int affected = chatMessageMapper.markAllReadFrom(Math.toIntExact(user.getPartnerId()), userId, LocalDateTime.now());
        if (affected == 0) {
            return; // 没有未读消息，无需通知
        }
        // 查询已读到的最大 ID（用于已读回执）
        Long maxReadId = chatMessageMapper.selectConversation(userId, Math.toIntExact(user.getPartnerId()), 0, Integer.MAX_VALUE)
                .stream()
                .filter(m -> m.getSenderId().equals(user.getPartnerId()) && m.getReceiverId().equals(userId))
                .map(ChatMessage::getId)
                .max(Long::compareTo)
                .orElse(null);

        WsChatMessage out = new WsChatMessage();
        out.setType("READ");
        out.setSenderId(userId);
        out.setReceiverId(Math.toIntExact(user.getPartnerId()));
        out.setLastReadId(maxReadId);
        sendTo(session, Math.toIntExact(user.getPartnerId()), out);
    }

    private void handleDelete(WebSocketSession session, Integer userId, WsChatMessage in) {
        // WS 软删除（单条，按用户维度），用于客户端在已开 WS 时即时同步
        if (in.getId() == null) {
            sendError(session, "DELETE 缺少 messageId");
            return;
        }
        chatMessageMapper.softDelete(in.getId(), userId);
        // 幂等：INSERT IGNORE 重复记录也视为成功
        // 给发送者回执成功
        WsChatMessage ack = new WsChatMessage();
        ack.setType("DELETE_ACK");
        ack.setId(in.getId());
        ack.setSenderId(userId);
        sendTo(session, userId, ack);
    }

    private void handleRecall(WebSocketSession session, Integer userId, WsChatMessage in) {
        if (in.getId() == null) {
            sendError(session, "RECALL 缺少 messageId");
            return;
        }
        ChatMessage msg = chatMessageMapper.selectById(in.getId());
        if (msg == null) {
            sendError(session, "消息不存在");
            return;
        }
        if (!userId.equals(msg.getSenderId())) {
            sendError(session, "只能撤回自己的消息");
            return;
        }
        // 限制：消息发出后 2 分钟内才允许撤回
        if (msg.getCreatedAt() != null &&
            msg.getCreatedAt().plusMinutes(2).isBefore(LocalDateTime.now())) {
            sendError(session, "超过 2 分钟的消息无法撤回");
            return;
        }
        int affected = chatMessageMapper.revoke(in.getId(), userId, LocalDateTime.now());
        if (affected == 0) {
            sendError(session, "撤回失败");
            return;
        }
        // 广播 RECALL 给双方：发送者 + 接收者
        WsChatMessage out = new WsChatMessage();
        out.setType("RECALL");
        out.setId(in.getId());
        out.setSenderId(msg.getSenderId());
        out.setReceiverId(msg.getReceiverId());
        sendTo(session, msg.getSenderId(), out);
        sendTo(session, msg.getReceiverId(), out);
    }

    private void sendTo(WebSocketSession session, Integer userId, WsChatMessage payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            if (userId.equals(((Integer) session.getAttributes().get(ChatHandshakeInterceptor.ATTR_USER_ID)))) {
                // 发送者自身：直接回写当前 Session
                synchronized (session) {
                    if (session.isOpen()) {
                        session.sendMessage(new TextMessage(json));
                    }
                }
            } else {
                sessionRegistry.sendTo(userId, json);
            }
        } catch (Exception e) {
            log.warn("聊天消息发送失败, userId: {}", userId, e);
        }
    }

    private void sendText(WebSocketSession session, Map<String, Object> payload) {
        try {
            synchronized (session) {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
                }
            }
        } catch (Exception e) {
            log.warn("聊天消息发送失败", e);
        }
    }

    private void sendError(WebSocketSession session, String message) {
        Map<String, Object> err = new HashMap<>();
        err.put("type", "ERROR");
        err.put("error", message);
        sendText(session, err);
    }
}