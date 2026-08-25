package com.example.lovemap.model.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * 用户信息更新DTO
 */
@Data
public class UserProfileUpdateDTO {

    /**
     * 昵称
     */
    private String nickname;

    /**
     * 手机号
     */
    private String phone;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 当前密码
     */
    private String password;

    /**
     * 新密码
     */
    private String newPassword;

    /**
     * 确认密码
     */
    private String confirmPassword;
}
