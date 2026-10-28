package com.example.lovemap.model.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 积分流水
 * 对应数据库表：user_points_log
 *
 * 设计要点：
 *   - 每次积分变动都落一条流水，便于客服查询与对账
 *   - change_points：正=获得，负=消耗
 *   - balance_after：冗余当前余额快照
 */
@Data
public class UserPointsLog {

    private Long id;
    private Long userId;

    /** 变动类型：1=签到 2=完成任务 3=兑换商品 4=管理员调整 */
    private Integer changeType;

    /** 变动积分（正=获得，负=消耗） */
    private Integer changePoints;

    /** 变动后余额 */
    private Integer balanceAfter;

    /** 关联类型：check_in/task/exchange/admin */
    private String refType;

    /** 关联 ID */
    private Long refId;

    /** 备注 */
    private String remark;

    private LocalDateTime createdAt;
}
