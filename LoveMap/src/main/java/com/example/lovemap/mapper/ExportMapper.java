package com.example.lovemap.mapper;

import com.example.lovemap.model.entity.ExportRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 导出任务 Mapper
 */
@Mapper
public interface ExportMapper {

    /**
     * 插入导出任务
     */
    int insert(ExportRecord record);

    /**
     * 根据ID查询
     */
    ExportRecord selectById(@Param("id") Long id);

    /**
     * 查询用户的导出任务列表（按创建时间降序）
     */
    List<ExportRecord> selectByUserId(@Param("userId") Long userId);

    /**
     * 查询指定时间之前已完成/失败的过期导出记录
     */
    List<ExportRecord> findExpiredBefore(@Param("deadline") LocalDateTime deadline);

    /**
     * 更新任务状态
     */
    int updateStatus(ExportRecord record);

    /**
     * 更新任务完成信息（状态、文件路径、文件大小、完成时间）
     */
    int updateCompleted(ExportRecord record);

    /**
     * 根据ID删除导出记录
     */
    int deleteById(@Param("id") Long id);
}