package com.example.lovemap.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * SSE 新通知事件 VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SseNotificationVO {

    /**
     * 通知ID
     */
    private Long id;

    /**
     * 通知类型：1-系统通知，2-纪念日提醒，3-绑定相关，4-照片相关
     */
    private Integer type;

    /**
     * 类型名称
     */
    private String typeName;

    /**
     * 通知标题
     */
    private String title;

    /**
     * 通知内容
     */
    private String content;

    /**
     * 关联业务ID
     */
    private Long businessId;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}
