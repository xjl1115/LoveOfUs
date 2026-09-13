package com.example.lovemap.model.entity;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 约会计划实体
 * 对应数据库表：date_plans
 */
@Data
public class DatePlan {

    /**
     * 主键
     */
    private Long id;

    /**
     * 所属情侣组 ID
     */
    private Long groupId;

    /**
     * 约会标题
     */
    private String title;

    /**
     * 计划日期
     */
    private LocalDate planDate;

    /**
     * 约会地点（旧字段，保留兼容）
     */
    private String location;

    /**
     * 时段：morning/noon/afternoon/evening/night
     */
    private String timeSlot;

    /**
     * 场景标签 JSON 字符串
     */
    private String scenes;

    /**
     * 地点建议
     */
    private String locationSuggestion;

    /**
     * 预算等级：free/low/mid/high/luxury
     */
    private String budget;

    /**
     * 推荐理由
     */
    private String reason;

    /**
     * 小贴士 JSON 字符串
     */
    private String tips;

    /**
     * 约会照片 URL JSON 字符串
     */
    private String photos;

    /**
     * 约会描述/备注（旧字段，保留兼容）
     */
    private String description;

    /**
     * 状态：0-计划中，1-已完成，2-已取消
     */
    private Integer status;

    /**
     * 创建人用户 ID
     */
    private Long createdBy;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

    /**
     * 软删除：0-未删除，1-已删除
     */
    private Integer isDeleted;

    /**
     * 删除时间
     */
    private LocalDateTime deletedAt;
}
