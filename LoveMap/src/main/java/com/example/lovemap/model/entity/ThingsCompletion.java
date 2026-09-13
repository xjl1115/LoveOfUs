package com.example.lovemap.model.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 情侣必做事项完成记录
 * 对应数据库表：things_completion（新增）
 *
 * 设计要点：
 *   - 以 groupId 为单位，情侣两人共享同一份 100 项的完成状态
 *   - 一个 thing + 一个 group 最多一条完成记录（唯一约束）
 *   - 首次标记完成时插入，取消完成时删除（避免同一事项重复累赘）
 */
@Data
public class ThingsCompletion {

    /**
     * 主键
     */
    private Long id;

    /**
     * 事项ID（关联 things.id）
     */
    private Long thingId;

    /**
     * 群组ID（情侣共享）
     */
    private Long groupId;

    /**
     * 完成人用户ID（情侣双方中哪一方点的完成）
     */
    private Long completedBy;

    /**
     * 完成时间
     */
    private LocalDateTime completedAt;

    /**
     * 完成笔记
     */
    private String note;
}