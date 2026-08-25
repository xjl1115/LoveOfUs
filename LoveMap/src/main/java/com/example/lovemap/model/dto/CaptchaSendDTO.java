package com.example.lovemap.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 发送验证码请求DTO
 * 支持邮箱和手机号两种方式
 */
@Data
public class CaptchaSendDTO {

    /**
     * 目标地址：手机号或邮箱地址
     */
    @NotBlank(message = "目标地址不能为空")
    private String target;

    /**
     * 发送渠道：sms-短信，email-邮箱
     */
    @NotBlank(message = "发送渠道不能为空")
    private String channel;

    /**
     * 验证码类型：register-注册，login-登录，reset_password-重置密码，bind_email-绑定邮箱，bind_phone-绑定手机
     */
    @NotBlank(message = "验证码类型不能为空")
    private String type;
}
