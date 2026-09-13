package com.example.lovemap.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 聊天消息 VO（含发送者信息）
 */
@Data
public class ChatMessageVO {

    /** 消息ID */
    private Long id;

    /** 发送者用户ID */
    private Integer senderId;

    /** 接收者用户ID */
    private Integer receiverId;

    /** 消息内容 */
    private String content;

    /** 卡片主图 URL（msg_type=5 时为改造图） */
    private String imageUrl;

    /** 消息类型：1=文本 / 5=妆造卡片 */
    private Integer msgType;

    /** 卡片附加 JSON（msg_type=5 时存 faceFeatures + suggestions 摘要） */
    private String extraJson;

    /** 是否已读 */
    private Integer isRead;

    /** 消息发送时间 */
    private LocalDateTime createdAt;

    /** 接收者已读时间 */
    private LocalDateTime readAt;

    /** 是否撤回：0=否 1=是 */
    private Integer revoked;

    /** 撤回时间 */
    private LocalDateTime revokedAt;
}