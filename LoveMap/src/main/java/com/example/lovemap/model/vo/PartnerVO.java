package com.example.lovemap.model.vo;

import lombok.Data;

/**
 * 伴侣信息VO
 */
@Data
public class PartnerVO {

    /**
     * 伴侣用户ID
     */
    private Long id;

    /**
     * 伴侣昵称
     */
    private String nickname;

    /**
     * 伴侣头像URL
     */
    private String avatarUrl;

    /**
     * 手机号（脱敏）
     */
    private String phone;

    /**
     * 邮箱
     */
    private String email;
}
