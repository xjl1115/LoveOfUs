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

    /**
     * 性别：0-未知/未设置，1-男，2-女
     */
    private Integer gender;

    /**
     * VIP 等级：0-普通，1-周卡，2-月卡，3-季卡，4-年卡，5-永久
     * <p>
     * VIP 归属情侣组，此处与本人展示同一组内生效等级
     */
    private Integer vipLevel;

    /**
     * VIP 等级名称
     */
    private String vipLevelName;
}
