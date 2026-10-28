package com.example.lovemap.model.dto;

import com.example.lovemap.common.validation.PhoneOrMasked;
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
     * 允许为空、11 位合法手机号，或后端脱敏占位串（如 182****1872）。
     * 脱敏串由 Service 层识别后跳过更新，避免误覆盖真实手机号。
     */
    @PhoneOrMasked
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
