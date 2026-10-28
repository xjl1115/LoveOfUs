package com.example.lovemap.mapper;

import com.example.lovemap.model.entity.UserPoints;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 用户积分账户 Mapper
 */
@Mapper
public interface UserPointsMapper {

    /**
     * 按用户 ID 查询（行不存在时返回 null）
     */
    UserPoints selectByUserId(@Param("userId") Long userId);

    /**
     * 初始化账户（若已存在则忽略）
     */
    int insertIgnore(@Param("userId") Long userId);

    /**
     * 原子增加积分与 total_points，同时更新连续签到天数与最后签到日期
     * 用于签到；连续天数/累计天数/最后签到日由调用方算出后整段覆盖
     *
     * @return 受影响行数（应为 1）
     */
    int incrPoints(@Param("userId") Long userId,
                   @Param("delta") int delta,
                   @Param("continuousDays") int continuousDays,
                   @Param("totalCheckInDays") int totalCheckInDays,
                   @Param("lastCheckInAt") java.time.LocalDate lastCheckInAt);

    /**
     * 原子扣减积分（兑换商品），保证余额足够
     * 仅当 points >= #{cost} 时才会扣减成功
     *
     * @return 受影响行数（0=余额不足）
     */
    int decrPoints(@Param("userId") Long userId, @Param("cost") int cost);

    /**
     * 原子增加积分（仅累加 points / total_points，不动签到相关字段）
     * 用于任务完成、管理员调整等不影响连续签到的场景
     */
    int addPointsOnly(@Param("userId") Long userId, @Param("delta") int delta);

    /**
     * 原子扣减积分（仅扣 points，不动 total_points 与签到字段），允许 delta 为负数
     * 仅当 points + delta >= 0 时成功
     *
     * @return 受影响行数（0=扣减后余额为负，禁止）
     */
    int subtractPointsOnly(@Param("userId") Long userId, @Param("delta") int delta);
}
