package com.example.lovemap.model.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

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
     * 性别：0-未知/未设置，1-男，2-女
     */
    private Integer gender;

    /**
     * 解除绑定状态
     */
    private UnbindStatusVO unbindStatus;

    /**
     * 统计数据
     */
    private UserStatsVO stats;

    /**
     * VIP 等级：0-普通用户，1-周卡，2-月卡，3-季卡，4-年卡，5-永久
     */
    private Integer vipLevel;

    /**
     * VIP 等级名称
     */
    private String vipLevelName;

    /**
     * VIP 到期时间，未开通或永久卡为 null
     */
    private LocalDateTime vipExpireAt;
}
