package com.example.lovemap.model.dto;

import lombok.Data;

/**
 * 登录请求DTO
 */
@Data
public class LoginDTO {

    /**
     * 账号（手机号或邮箱）
     */
    private String account;

    /**
     * 密码（密码登录时必填）
     */
    private String password;

    /**
     * 验证码（快捷登录必填，密码登录可选）
     */
    private String captcha;

    /**
     * 登录方式：password-密码登录，captcha-验证码快捷登录
     */
    private String loginType;
}
