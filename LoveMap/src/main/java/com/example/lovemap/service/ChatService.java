package com.example.lovemap.service;

import com.example.lovemap.common.Result;
import com.example.lovemap.model.dto.ChatPresenceDTO;
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

    /**
     * 进入聊天页面（注册"在线于聊天页"状态，并刷新心跳）
     */
    Result<Void> enterChat(Integer userId, ChatPresenceDTO dto);

    /**
     * 心跳刷新
     */
    Result<Void> heartbeat(Integer userId, ChatPresenceDTO dto);

    /**
     * 离开聊天页面
     */
    Result<Void> leaveChat(Integer userId, ChatPresenceDTO dto);

    /**
     * 软删除单条消息（仅本人发送）
     */
    Result<Boolean> deleteMessage(Integer userId, Long id);

    /**
     * 批量软删除（仅本人发送）
     */
    Result<Integer> deleteMessages(Integer userId, java.util.List<Long> ids);

    /**
     * 撤回单条消息（仅本人发送且 2 分钟内）
     */
    Result<Boolean> recallMessage(Integer userId, Long id);
}
