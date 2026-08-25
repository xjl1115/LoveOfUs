package com.example.lovemap.model.vo;

import lombok.Data;

import java.time.LocalDate;

/**
 * 用户信息VO
 */
@Data
public class UserVO {

    /**
     * 用户ID
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
     * 手机号（脱敏）
     */
    private String phone;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 是否已绑定伴侣
     */
    private Boolean isBound;

    /**
     * 情侣ID（关联 group 表的主键 id）
     */
    private Long groupId;

    /**
     * 绑定码信息（未绑定时返回）
     */
    private BindCodeVO bindCode;

    /**
     * 伴侣信息（已绑定时返回）
     */
    private PartnerVO partner;

    /**
     * 在一起的天数
     */
    private Integer daysTogether;

    /**
     * 关系开始日期
     */
    private LocalDate relationshipStart;

    /**
     * 解除绑定状态
     */
    private UnbindStatusVO unbindStatus;

    /**
     * 统计数据
     */
    private UserStatsVO stats;
}
