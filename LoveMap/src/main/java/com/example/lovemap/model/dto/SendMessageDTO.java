package com.example.lovemap.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 发送消息请求 DTO
 */
@Data
public class SendMessageDTO {

    /** 客户端生成的临时ID（用于去重/关联前后端消息） */
    private String clientMsgId;

    /** 消息内容 */
    @NotBlank(message = "消息内容不能为空")
    @Size(max = 2000, message = "消息内容不能超过2000字符")
    private String content;

    /** 消息类型：1=文本，默认1 */
    private Integer msgType = 1;
}
