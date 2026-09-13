package com.example.lovemap.makeover.mapper;

import com.example.lovemap.model.entity.MakeoverTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * AI 化妆建议异步任务 Mapper
 */
@Mapper
public interface MakeoverTaskMapper {

    /**
     * 新建任务
     */
    int insert(MakeoverTask task);

    /**
     * 标记任务开始
     */
    int markRunning(@Param("id") Long id, @Param("startedAt") LocalDateTime startedAt);

    /**
     * 标记任务成功
     */
    int markSuccess(@Param("id") Long id, @Param("finishedAt") LocalDateTime finishedAt);

    /**
     * 标记任务失败
     */
    int markFailed(@Param("id") Long id,
                   @Param("errorMessage") String errorMessage,
                   @Param("finishedAt") LocalDateTime finishedAt);

    /**
     * 增加重试次数
     */
    int incrementRetry(@Param("id") Long id);
}