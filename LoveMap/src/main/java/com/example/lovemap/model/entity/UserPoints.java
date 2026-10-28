package com.example.lovemap.model.entity;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 用户积分账户
 * 对应数据库表：user_points
 *
 * 设计要点：
 *   - 按 user_id 主键，每用户一行；首次查询时若不存在则懒加载插入
 *   - 积分变动走原子 UPDATE，不在 Java 层做读-改-写
 */
@Data
public class UserPoints {

    /** 用户 ID（主键，与 user.id 对齐） */
    private Long userId;

    /** 当前可用积分 */
    private Integer points;

    /** 历史累计获得积分 */
    private Integer totalPoints;

    /** 当前连续签到天数（断签清零） */
    private Integer continuousDays;

    /** 累计签到天数 */
    private Integer totalCheckInDays;

    /** 上次签到日期（DATE 类型） */
    private LocalDate lastCheckInAt;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
