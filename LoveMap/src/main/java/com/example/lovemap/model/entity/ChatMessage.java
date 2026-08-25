package com.example.lovemap.model.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 聊天消息实体（伴侣 1 对 1）
 */
@Data
public class ChatMessage {

    /** 消息ID */
    private Long id;

    /** 发送者用户ID */
    private Integer senderId;

    /** 接收者用户ID */
    private Integer receiverId;

    /** 消息内容 */
    private String content;

    /** 消息类型：1=文本 */
    private Integer msgType;

    /** 接收者是否已读：0=未读 1=已读 */
    private Integer isRead;

    /** 接收者已读时间 */
    private LocalDateTime readAt;

    /** 消息发送时间 */
    private LocalDateTime createdAt;
}