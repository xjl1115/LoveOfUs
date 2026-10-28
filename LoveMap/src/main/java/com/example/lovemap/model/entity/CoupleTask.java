package com.example.lovemap.model.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 情侣任务定义
 * 对应数据库表：couple_task
 *
 * 设计要点：
 *   - 顾问控制台维护，前端只读
 *   - status = 0 禁用，前端不返回
 *   - completeType 决定由哪个系统事件自动识别完成（不再由用户手动点完成）
 *   - periodType 决定任务周期：日 / 周 / 月，配合 user_task.period_key 使用
 */
@Data
public class CoupleTask {

    private Long id;
    private String content;
    private String description;
    private String icon;
    /** 完成可获得积分 */
    private Integer points;
    /** 0=禁用 1=启用 */
    private Integer status;
    /** 完成方式：manual/message_send/photo_upload/date_plan_create */
    private String completeType;
    /** 周期：daily/weekly/monthly */
    private String periodType;
    /** 1=需手动领取积分 0=完成即发放 */
    private Integer claimRequired;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
