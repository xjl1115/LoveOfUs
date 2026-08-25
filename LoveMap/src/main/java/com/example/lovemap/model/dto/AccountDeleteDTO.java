package com.example.lovemap.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 注销账号 DTO
 */
@Data
@Schema(description = "注销账号请求")
public class AccountDeleteDTO {

    @NotBlank(message = "密码不能为空")
    @Schema(description = "当前密码，用于确认身份")
    private String password;
}