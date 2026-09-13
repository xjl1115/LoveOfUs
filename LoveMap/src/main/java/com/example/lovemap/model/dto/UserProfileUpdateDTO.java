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
     * 性别：0-未知/未设置，1-男，2-女
     */
    private Integer gender;

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
