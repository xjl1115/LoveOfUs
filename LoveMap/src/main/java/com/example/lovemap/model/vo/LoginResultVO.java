package com.example.lovemap.model.vo;

import lombok.Data;

/**
 * 登录/注册结果VO
 */
@Data
public class LoginResultVO {

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * JWT Token
     */
    private String token;

    /**
     * 刷新 Token（长期有效，用于无感刷新）
     */
    private String refreshToken;

    /**
     * Token有效期（秒）
     */
    private Long expiresIn;

    /**
     * 用户信息
     */
    private UserInfoVO userInfo;

    /**
     * 用户信息简要VO
     */
    @Data
    public static class UserInfoVO {
        /**
         * 昵称
         */
        private String nickname;

        /**
         * 头像URL
         */
        private String avatarUrl;

        /**
         * 是否已绑定伴侣
         */
        private Boolean isBound;
    }
}
