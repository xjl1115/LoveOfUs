package com.example.lovemap.mapper;

import com.example.lovemap.model.entity.CoupleTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 情侣任务 Mapper
 */
@Mapper
public interface CoupleTaskMapper {

    /** 全部（含禁用） */
    List<CoupleTask> selectAll();

    /** 已启用 */
    List<CoupleTask> selectEnabled();

    /** 已启用且完成方式匹配（供系统事件自动识别） */
    List<CoupleTask> selectEnabledByCompleteType(@Param("completeType") String completeType);

    /** 按 ID 查询 */
    CoupleTask selectById(@Param("id") Long id);

    /** 新增 */
    int insert(CoupleTask task);

    /** 更新 */
    int update(CoupleTask task);

    /** 删除 */
    int deleteById(@Param("id") Long id);
}
