package com.example.lovemap.mapper;

import com.example.lovemap.model.entity.ChatMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 聊天消息 Mapper
 */
@Mapper
public interface ChatMessageMapper {

    /**
     * 插入消息（useGeneratedKeys 拿主键）
     */
    int insert(ChatMessage message);

    /**
     * 根据 ID 查询
     */
    ChatMessage selectById(@Param("id") Long id);

    /**
     * 查询两个用户之间的会话消息（按时间正序）
     *
     * @param userId   当前用户
     * @param peerId   对方用户（伴侣）
     * @param offset   偏移
     * @param limit    条数
     */
    List<ChatMessage> selectConversation(
            @Param("userId") Integer userId,
            @Param("peerId") Integer peerId,
            @Param("offset") int offset,
            @Param("limit") int limit);

    /**
     * 查询对方发给当前用户、且未读的消息数量
     */
    long countUnreadFrom(@Param("senderId") Integer senderId, @Param("receiverId") Integer receiverId);

    /**
     * 将对方发来的所有未读消息标记为已读
     */
    int markAllReadFrom(@Param("senderId") Integer senderId,
                        @Param("receiverId") Integer receiverId,
                        @Param("readAt") LocalDateTime readAt);

    /**
     * 标记单条已读
     */
    int markRead(@Param("id") Long id, @Param("receiverId") Integer receiverId, @Param("readAt") LocalDateTime readAt);
}
