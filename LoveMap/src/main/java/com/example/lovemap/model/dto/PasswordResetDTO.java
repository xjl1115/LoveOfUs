package com.example.lovemap.model.dto;

import lombok.Data;

/**
 * 重置密码请求DTO
 */
@Data
public class PasswordResetDTO {

    /**
     * 目标地址：手机号或邮箱地址
     */
    private String target;

    /**
     * 发送渠道：sms-短信，email-邮箱
     */
    private String channel;

    /**
     * 验证码
     */
    private String captcha;

    /**
     * 新密码，8-20位
     */
    private String newPassword;

    /**
     * 确认新密码
     */
    private String confirmPassword;
}
