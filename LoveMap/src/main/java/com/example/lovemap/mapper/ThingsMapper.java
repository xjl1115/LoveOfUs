package com.example.lovemap.mapper;

import com.example.lovemap.model.entity.Things;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 情侣必做 100 件事 Mapper
 */
@Mapper
public interface ThingsMapper {

    /**
     * 批量插入（初始化种子数据用）
     */
    int batchInsert(@Param("list") List<Things> list);

    /**
     * 查询全部 100 项（按 id 升序）
     */
    List<Things> selectAll();

    /**
     * 统计总数
     */
    long countAll();

    /**
     * 根据 ID 查询
     */
    Things selectById(@Param("id") Long id);

    /**
     * 更新事项的完成标记
     */
    int updateCompleted(@Param("id") Long id, @Param("completed") Integer completed);
}