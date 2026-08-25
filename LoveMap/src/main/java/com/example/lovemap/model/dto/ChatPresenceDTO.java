package com.example.lovemap.model.dto;

import lombok.Data;

/**
 * 聊天页面在线状态请求 DTO
 */
@Data
public class ChatPresenceDTO {

    /**
     * 对话的伴侣 ID（用于校验是否还在同一个对话窗口）
     */
    private Integer partnerId;
}