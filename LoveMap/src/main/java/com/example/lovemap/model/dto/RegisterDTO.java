package com.example.lovemap.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 注册请求DTO
 */
@Data
public class RegisterDTO {

    /**
     * 昵称，2-20字符
     */
    @NotBlank(message = "昵称不能为空")
    @Size(min = 2, max = 20, message = "昵称长度需在2-20个字符之间")
    private String nickname;

    /**
     * 手机号（可选）
     */
    private String phone;

    /**
     * 邮箱
     */
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;

    /**
     * 密码，6位以上
     */
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, message = "密码长度不能少于6位")
    private String password;

    /**
     * 确认密码
     */
    @NotBlank(message = "确认密码不能为空")
    private String confirmPassword;

    /**
     * 验证码，6位数字
     */
    @NotBlank(message = "验证码不能为空")
    private String captcha;

    /**
     * 伴侣绑定码（可选）
     */
    private String partnerCode;
}
