package com.example.lovemap.model.entity;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 纪念日实体类
 * 对应数据库表：anniversary
 */
@Data
public class Anniversary {

    /**
     * 主键
     */
    private Long id;

    /**
     * 所属群组ID（情侣共享；未绑定情侣时为 null）
     */
    private Long groupId;

    /**
     * 创建人用户ID（仅未绑定情侣的个人纪念日使用）
     */
    private Long userId;

    /**
     * 纪念日标题
     */
    private String name;

    /**
     * 纪念日日期
     */
    private LocalDate anniversaryDate;

    /**
     * 是否每年重复提醒
     */
    private Boolean isRecurring;

    /**
     * 提醒提前天数
     */
    private Integer remindDays;

    /**
     * 备注描述
     */
    private String description;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
