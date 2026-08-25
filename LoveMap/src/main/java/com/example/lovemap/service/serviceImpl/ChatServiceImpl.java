package com.example.lovemap.service.serviceImpl;

import com.example.lovemap.common.PageResult;
import com.example.lovemap.common.Result;
import com.example.lovemap.mapper.ChatMessageMapper;
import com.example.lovemap.mapper.UserMapper;
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
