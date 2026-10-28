package com.example.lovemap.mapper;

import com.example.lovemap.model.entity.UserTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 用户任务状态 Mapper
 *
 * 并发安全：完成 / 领取均使用带 status 条件的 UPDATE 并判断受影响行数，
 * 避免「先查再改」的竞态；行不存在时先 INSERT IGNORE 再 UPDATE，保证幂等。
 */
@Mapper
public interface UserTaskMapper {

    /** 幂等落行（行已存在时忽略） */
    int insertIgnore(@Param("userId") Long userId,
                     @Param("taskId") Long taskId,
                     @Param("periodType") String periodType,
                     @Param("periodKey") String periodKey);

    /** 标记完成：0 → 1，返回受影响行数（0=已完成/已领取，无需重复处理） */
    int markCompleted(@Param("userId") Long userId,
                      @Param("taskId") Long taskId,
                      @Param("periodKey") String periodKey);

    /** 标记已领取：1 → 2，返回受影响行数（0=状态不满足） */
    int markClaimed(@Param("userId") Long userId,
                    @Param("taskId") Long taskId,
                    @Param("periodKey") String periodKey,
                    @Param("pointsAwarded") Integer pointsAwarded);

    /** 查询用户在指定周期键下的任务状态行 */
    List<UserTask> selectByUserAndPeriodKeys(@Param("userId") Long userId,
                                             @Param("periodKeys") List<String> periodKeys);

    /**
     * 清理过期的日任务行（保留最近 N 天）
     *
     * @param beforeKey 早于该周期键（yyyy-MM-dd）的行将被删除
     */
    int deleteDailyBefore(@Param("beforeKey") String beforeKey);
}
