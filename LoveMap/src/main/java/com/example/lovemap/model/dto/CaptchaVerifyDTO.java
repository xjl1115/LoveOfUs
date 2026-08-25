package com.example.lovemap.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 验证验证码请求DTO
 */
@Data
public class CaptchaVerifyDTO {

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
     * 验证码
     */
    @NotBlank(message = "验证码不能为空")
    private String captcha;

    /**
     * 验证码类型
     */
    @NotBlank(message = "验证码类型不能为空")
    private String type;
}
