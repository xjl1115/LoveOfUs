package com.example.lovemap.service;

import com.example.lovemap.common.Result;
import com.example.lovemap.model.vo.ChatMessageVO;

/**
 * 聊天服务
 */
public interface ChatService {

    /**
     * 分页查询历史消息（双方的消息，按时间正序）
     */
    Result<com.example.lovemap.common.PageResult<ChatMessageVO>> getHistory(Integer userId, int page, int size);

    /**
     * 查询对方发给当前用户的未读消息数量
     */
    Result<Long> getUnreadCount(Integer userId);

    /**
     * 标记当前用户所有未读消息为已读
     */
    Result<Integer> markAllRead(Integer userId);
}
