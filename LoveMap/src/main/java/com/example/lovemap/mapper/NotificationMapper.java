package com.example.lovemap.mapper;

import com.example.lovemap.model.entity.Notification;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 通知 Mapper
 */
@Mapper
public interface NotificationMapper {

    /**
     * 插入通知
     */
    int insert(Notification notification);

    /**
     * 查询用户的通知列表（分页由 PageHelper 处理）
     */
    List<Notification> selectByUserId(@Param("userId") Integer userId,
                                      @Param("isRead") Integer isRead);

    /**
     * 统计用户的通知总数
     */
    long countByUserId(@Param("userId") Integer userId, @Param("isRead") Integer isRead);

    /**
     * 查询用户的未读通知数量
     */
    long countUnreadByUserId(@Param("userId") Integer userId);

    /**
     * 标记通知为已读
     */
    int markAsRead(@Param("id") Integer id, @Param("userId") Integer userId);

    /**
     * 标记用户所有通知为已读
     */
    int markAllAsRead(@Param("userId") Integer userId);

    /**
     * 删除通知
     */
    int deleteById(@Param("id") Integer id, @Param("userId") Integer userId);

    /**
     * 删除用户的所有已读通知
     */
    int deleteReadByUserId(@Param("userId") Integer userId);

    /**
     * 根据ID和用户ID查询单个通知
     */
    Notification selectByIdAndUserId(@Param("id") Integer id, @Param("userId") Integer userId);
}
