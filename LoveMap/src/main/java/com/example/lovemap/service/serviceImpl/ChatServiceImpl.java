package com.example.lovemap.service.serviceImpl;

import com.example.lovemap.chat.ChatPresenceRegistry;
import com.example.lovemap.common.PageResult;
import com.example.lovemap.common.Result;
import com.example.lovemap.mapper.ChatMessageMapper;
import com.example.lovemap.mapper.UserMapper;
import com.example.lovemap.model.dto.ChatPresenceDTO;
import com.example.lovemap.model.entity.ChatMessage;
import com.example.lovemap.model.entity.User;
import com.example.lovemap.model.vo.ChatMessageVO;
import com.example.lovemap.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 聊天 Service 实现
 */
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final ChatMessageMapper chatMessageMapper;
    private final UserMapper userMapper;
    private final ChatPresenceRegistry chatPresenceRegistry;

    @Override
    public Result<PageResult<ChatMessageVO>> getHistory(Integer userId, int page, int size) {
        if (userId == null) {
            return Result.unauthorized("未登录");
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            return Result.notFound("用户不存在");
        }
        if (user.getPartnerId() == null) {
            return Result.badRequest("请先绑定伴侣");
        }

        if (page < 1) page = 1;
        if (size < 1) size = 20;
        if (size > 100) size = 100;
        int offset = (page - 1) * size;

        List<ChatMessage> list = chatMessageMapper.selectConversation(userId, user.getPartnerId().intValue(), offset, size);
        // total = offset + size，让前端能继续加载更多（不返回精确总数，避免每次 count 扫描）
        long displayTotal = offset + list.size();

        List<ChatMessageVO> vos = list.stream().map(this::toVO).toList();
        return Result.page(vos, displayTotal, page, size);
    }

    @Override
    public Result<Long> getUnreadCount(Integer userId) {
        if (userId == null) {
            return Result.unauthorized("未登录");
        }
        User user = userMapper.selectById(userId);
        if (user == null || user.getPartnerId() == null) {
            return Result.success(0L);
        }
        long count = chatMessageMapper.countUnreadFrom(user.getPartnerId().intValue(), userId);
        return Result.success(count);
    }

    @Override
    public Result<Integer> markAllRead(Integer userId) {
        if (userId == null) {
            return Result.unauthorized("未登录");
        }
        User user = userMapper.selectById(userId);
        if (user == null || user.getPartnerId() == null) {
            return Result.success(0);
        }
        int affected = chatMessageMapper.markAllReadFrom(user.getPartnerId().intValue(), userId, LocalDateTime.now());
        return Result.success(affected);
    }

    @Override
    public Result<Void> enterChat(Integer userId, ChatPresenceDTO dto) {
        if (userId == null) {
            return Result.unauthorized("未登录");
        }
        if (dto == null || dto.getPartnerId() == null) {
            return Result.badRequest("partnerId 不能为空");
        }
        chatPresenceRegistry.enter(userId, dto.getPartnerId());
        return Result.success();
    }

    @Override
    public Result<Void> heartbeat(Integer userId, ChatPresenceDTO dto) {
        if (userId == null) {
            return Result.unauthorized("未登录");
        }
        // 心跳：若 entry 仍存在则刷新时间戳
        chatPresenceRegistry.heartbeat(userId);
        return Result.success();
    }

    @Override
    public Result<Void> leaveChat(Integer userId, ChatPresenceDTO dto) {
        if (userId == null) {
            return Result.unauthorized("未登录");
        }
        chatPresenceRegistry.leave(userId);
        return Result.success();
    }

    @Override
    public Result<Boolean> deleteMessage(Integer userId, Long id) {
        if (userId == null || id == null) {
            return Result.badRequest("参数错误");
        }
        // 任何登录用户都可以“软删除”任意消息——删除者视图不再显示，对方仍可见（按用户维度）
        chatMessageMapper.softDelete(id, userId);
        // 幂等：已存在记录（INSERT IGNORE）也算成功
        return Result.success(true);
    }

    @Override
    public Result<Integer> deleteMessages(Integer userId, java.util.List<Long> ids) {
        if (userId == null || ids == null || ids.isEmpty()) {
            return Result.badRequest("参数错误");
        }
        chatMessageMapper.softDeleteBatch(ids, userId);
        return Result.success(ids.size());
    }

    @Override
    public Result<Boolean> recallMessage(Integer userId, Long id) {
        if (userId == null || id == null) {
            return Result.badRequest("参数错误");
        }
        // 撤回窗口 2 分钟
        ChatMessage msg = chatMessageMapper.selectById(id);
        if (msg == null || !msg.getSenderId().equals(userId)) {
            return Result.badRequest("消息不存在或无权操作");
        }
        if (msg.getCreatedAt() != null &&
                msg.getCreatedAt().plusMinutes(2).isBefore(LocalDateTime.now())) {
            return Result.badRequest("超过 2 分钟无法撤回");
        }
        int n = chatMessageMapper.revoke(id, userId, LocalDateTime.now());
        return Result.success(n > 0);
    }

    private ChatMessageVO toVO(ChatMessage m) {
        ChatMessageVO vo = new ChatMessageVO();
        vo.setId(m.getId());
        vo.setSenderId(m.getSenderId());
        vo.setReceiverId(m.getReceiverId());
        vo.setContent(m.getContent());
        vo.setMsgType(m.getMsgType());
        vo.setIsRead(m.getIsRead());
        vo.setCreatedAt(m.getCreatedAt());
        vo.setReadAt(m.getReadAt());
        return vo;
    }
}
