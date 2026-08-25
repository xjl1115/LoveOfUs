package com.example.lovemap.chat;

import lombok.Data;

/**
 * WebSocket 聊天消息协议
 * <p>
 * 客户端->服务端 type:
 *   - CHAT       普通聊天消息（必填 clientMsgId/content）
 *   - TYPING     打字中状态（无需 content）
 *   - READ       标记已读（无需 content）
 *   - PING       心跳
 * <p>
 * 服务端->客户端 type:
 *   - CHAT       服务端已存储的新消息（含 id / createdAt / isRead）
 *   - TYPING     对端打字状态
 *   - READ       已读回执（lastReadId 为已读到的最大消息ID）
 *   - PONG       心跳回复
 *   - ERROR      错误消息
 */
@Data
public class WsChatMessage {

    /** 消息类型 */
    private String type;

    /** 客户端生成的临时 ID（去重 + 关联前后端消息） */
    private String clientMsgId;

    /** 服务端消息 ID（仅服务端推送消息有） */
    private Long id;

    /** 发送者 */
    private Integer senderId;

    /** 接收者 */
    private Integer receiverId;

    /** 消息内容 */
    private String content;

    /** 消息类型：1=文本 */
    private Integer msgType;

    /** 是否已读 */
    private Integer isRead;

    /** 已读到的最大消息ID（READ 回执） */
    private Long lastReadId;

    /** 消息发送时间 */
    private String createdAt;

    /** 错误消息（type=ERROR 时） */
    private String error;
}