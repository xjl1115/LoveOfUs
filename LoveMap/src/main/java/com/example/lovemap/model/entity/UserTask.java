package com.example.lovemap.model.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户任务状态
 * 对应数据库表：user_task
 *
 * 设计要点：
 *   - 以 (user_id, task_id, period_key) 唯一，周/月任务复用同表
 *   - status 状态机：0=未完成 → 1=已完成待领取 → 2=已领取
 *   - 只有真正完成 / 领取时才落行，查询缺失行按 status=0 输出
 */
@Data
public class UserTask {

    private Long id;
    private Integer userId;
    private Long taskId;
    private String periodType;
    private String periodKey;
    /** 0=未完成 1=已完成待领取 2=已领取 */
    private Integer status;
    private Integer progress;
    private LocalDateTime completedAt;
    private LocalDateTime claimedAt;
    private Integer pointsAwarded;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
