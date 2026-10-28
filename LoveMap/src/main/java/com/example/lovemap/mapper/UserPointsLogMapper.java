package com.example.lovemap.mapper;

import com.example.lovemap.model.entity.UserPointsLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 积分流水 Mapper
 */
@Mapper
public interface UserPointsLogMapper {

    /**
     * 落一条流水
     */
    int insert(UserPointsLog log);

    /**
     * 按日期范围查询某用户的积分变动汇总（按天聚合）
     * 用于日历展示「每天获得的积分」
     * 返回字段：day (yyyy-MM-dd), score (当天获得积分总和)
     */
    java.util.List<java.util.Map<String, Object>> dailyScoreByMonth(
            @Param("userId") Long userId,
            @Param("startDate") java.time.LocalDate startDate,
            @Param("endDate") java.time.LocalDate endDate);

    /**
     * 按月份查询某用户的签到日期列表（yyyy-MM-dd）
     * 仅包含 change_type = 签到 且 change_points > 0 的有效签到
     */
    java.util.List<String> checkInDatesByMonth(
            @Param("userId") Long userId,
            @Param("startDate") java.time.LocalDate startDate,
            @Param("endDate") java.time.LocalDate endDate);
}
