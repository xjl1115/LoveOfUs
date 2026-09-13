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
     * 通知类型：1-系统通知，2-纪念日提醒，3-绑定相关，4-照片相关
     */
    private Integer type;

    /**
     * 类型名称（前端列表标签展示）
     */
    private String typeName;

    /**
     * 关联业务ID（纪念日提醒为纪念日ID）
     */
    private Long businessId;
    
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
