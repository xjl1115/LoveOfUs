package com.example.lovemap.model.vo;

import lombok.Data;

/**
 * 通知 VO
 */
@Data
public class NotificationVO {
    
    /**
     * 通知ID
     */
    private Integer id;
    
    /**
     * 通知内容
     */
    private String text;
    
    /**
     * 用户/伴侣ID
     */
    private Integer userId;
    
    /**
     * 是否已读
     */
    private Integer isRead;
    
    /**
     * 创建时间
     */
    private String createdAt;
}
