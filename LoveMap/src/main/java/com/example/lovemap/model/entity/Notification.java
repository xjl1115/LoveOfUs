package com.example.lovemap.model.entity;

import lombok.Data;

/**
 * 通知实体
 */
@Data
public class Notification {
    
    /**
     * 主键
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
     * 是否已读 0-未读，1-已读
     */
    private Integer isRead;
    
    /**
     * 创建时间
     */
    private String createdAt;
}
