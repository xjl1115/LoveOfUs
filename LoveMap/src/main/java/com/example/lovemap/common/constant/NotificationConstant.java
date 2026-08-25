package com.example.lovemap.common.constant;

/**
 * 通知相关常量
 */
public class NotificationConstant {

    /**
     * 通知未读缓存键前缀
     */
    public static final String NOTIFICATION_UNREAD_PREFIX = "notification:unread:";

    /**
     * 通知已读缓存键前缀
     */
    public static final String NOTIFICATION_READ_PREFIX = "notification:read:";

    /**
     * 通知未读数量缓存键前缀
     */
    public static final String NOTIFICATION_UNREAD_COUNT_PREFIX = "notification:unread:count:";

    private NotificationConstant() {
        // 防止实例化
    }
}
