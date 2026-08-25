package com.example.lovemap.model.vo;

import lombok.Data;
import java.util.List;

/**
 * 通知列表 VO
 */
@Data
public class NotificationListVO {
    
    /**
     * 总数
     */
    private Long total;
    
    /**
     * 未读数
     */
    private Long unreadCount;
    
    /**
     * 通知列表
     */
    private List<NotificationVO> list;
}
