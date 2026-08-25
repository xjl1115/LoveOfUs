package com.example.lovemap.model.dto;

import lombok.Data;

/**
 * 修改密码请求DTO（需登录）
 */
@Data
public class PasswordChangeDTO {

    /**
     * 旧密码
     */
    private String oldPassword;

    /**
     * 新密码
     */
    private String newPassword;

    /**
     * 确认新密码
     */
    private String confirmPassword;
}
