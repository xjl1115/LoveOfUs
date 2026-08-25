package com.example.lovemap.service;

import com.example.lovemap.common.Result;
import com.example.lovemap.model.entity.Notification;
import com.example.lovemap.model.vo.NotificationListVO;

/**
 * 通知服务接口
 */
public interface NotificationService {

    /**
     * 查询通知列表
     *
     * @param userId 用户ID
     * @param page 页码
     * @param size 每页数量
     * @param isRead 是否已读筛选（null表示全部，0未读，1已读）
     * @return 通知列表
     */
    Result<NotificationListVO> getNotificationList(Integer userId, Integer page, Integer size, Integer isRead);

    /**
     * 查询未读通知数量
     *
     * @param userId 用户ID
     * @return 未读数量
     */
    Result<Long> getUnreadCount(Integer userId);

    /**
     * 标记通知为已读
     *
     * @param userId 用户ID
     * @param notificationId 通知ID
     * @return 操作结果
     */
    Result<Void> markAsRead(Integer userId, Integer notificationId);

    /**
     * 标记所有通知为已读
     *
     * @param userId 用户ID
     * @return 标记数量
     */
    Result<Integer> markAllAsRead(Integer userId);

    /**
     * 删除通知
     *
     * @param userId 用户ID
     * @param notificationId 通知ID
     * @return 操作结果
     */
    Result<Void> deleteNotification(Integer userId, Integer notificationId);

    /**
     * 清空已读通知
     *
     * @param userId 用户ID
     * @return 删除数量
     */
    Result<Integer> clearReadNotifications(Integer userId);

    /**
     * 创建通知（存入数据库）
     *
     * @param userId 接收通知的用户ID
     * @param text 通知内容
     * @return 创建的通知
     */
    Notification createNotification(Integer userId, String text);

    /**
     * 创建通知并发送SSE推送
     *
     * @param userId 接收通知的用户ID
     * @param text 通知内容
     */
    void createAndPushNotification(Integer userId, String text);
}
