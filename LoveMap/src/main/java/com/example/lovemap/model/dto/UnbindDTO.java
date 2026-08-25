package com.example.lovemap.model.dto;

import lombok.Data;

/**
 * 解除绑定请求DTO
 */
@Data
public class UnbindDTO {

    /**
     * 是否确认解除
     */
    private Boolean confirm;

    /**
     * 解除原因（可选）
     */
    private String reason;
}
