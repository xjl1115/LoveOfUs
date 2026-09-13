package com.example.lovemap.model.entity;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 心愿清单实体
 * 对应数据库表：wishlist_items
 */
@Data
public class WishlistItem {

    /**
     * 主键
     */
    private Long id;

    /**
     * 所属情侣组 ID
     */
    private Long groupId;

    /**
     * 心愿标题
     */
    private String title;

    /**
     * 心愿描述
     */
    private String description;

    /**
     * 分类
     */
    private String category;

    /**
     * 图标
     */
    private String icon;

    /**
     * 目标值
     */
    private Integer targetValue;

    /**
     * 当前值
     */
    private Integer currentValue;

    /**
     * 单位
     */
    private String unit;

    /**
     * 优先级：0-普通，1-高，2-低
     */
    private Integer priority;

    /**
     * 状态：0-未完成，1-已完成
     */
    private Integer status;

    /**
     * 期望完成日期（deadline）
     */
    private LocalDate targetDate;

    /**
     * 是否需要双方确认：0-否，1-是
     */
    private Integer needBothConfirm;

    /**
     * 达成时间
     */
    private LocalDateTime achievedAt;

    /**
     * 达成笔记
     */
    private String achievedNote;

    /**
     * 达成纪念照片 URL（1 张）
     */
    private String achievedPhotoUrl;

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
