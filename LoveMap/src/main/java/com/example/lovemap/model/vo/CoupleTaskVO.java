package com.example.lovemap.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 情侣任务 VO（用户端）
 *
 * 状态机：0=未完成 → 1=已完成待领取 → 2=已领取
 *   taskStatus  当前状态
 *   completed   是否已完成（含已领取），用于前端展示「已完成 / 未完成」
 *   claimable   是否可领取积分（taskStatus=1 且任务需手动领取）
 *   autoDetect  是否由系统自动识别完成（false 时前端不展示完成态按钮）
 */
@Data
public class CoupleTaskVO {

    private Long id;
    private String content;
    private String description;
    private String icon;
    private Integer points;
    private Integer status;
    private Integer sortOrder;

    /** 完成方式：manual/message_send/photo_upload/date_plan_create */
    private String completeType;
    /** 周期：daily/weekly/monthly */
    private String periodType;
    /** 1=需手动领取积分 */
    private Integer claimRequired;
    /** 当前周期键（如 2026-10-17） */
    private String periodKey;

    /** 0=未完成 1=已完成待领取 2=已领取 */
    private Integer taskStatus;
    private Boolean completed;
    private Boolean claimable;
    private LocalDateTime completedAt;
    private LocalDateTime claimedAt;
    private Integer pointsAwarded;
}
