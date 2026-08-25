package com.example.lovemap.model.entity;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class User {

    /**
     * 主键
     */
    private Long id;

    /**
     * 昵称
     */
    private String nickname;

    /**
     * 头像URL
     */
    private String avatarUrl;

    /**
     * 手机号（登录凭证）
     */
    private String phone;

    /**
     * 邮箱（登录凭证）
     */
    private String email;

    /**
     * 密码哈希
     */
    private String password;

    /**
     * 绑定的伴侣用户ID
     */
    private Long partnerId;

    /**
     * 是否已绑定：0-未绑定，1-已绑定
     */
    private Integer isBound;

    /**
     * 情侣ID（关联 group 表的主键 id）
     */
    private Long groupId;

    /**
     * 在一起起始日期
     */
    private LocalDate relationshipStart;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

    /**
     * 软删除标记：0-未删除，1-已删除
     */
    private Integer isDeleted;

    /**
     * 删除时间
     */
    private LocalDateTime deletedAt;
}
