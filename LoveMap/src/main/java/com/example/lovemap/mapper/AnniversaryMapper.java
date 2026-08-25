package com.example.lovemap.mapper;

import com.example.lovemap.model.entity.Anniversary;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 纪念日 Mapper
 */
@Mapper
public interface AnniversaryMapper {

    /**
     * 插入纪念日
     */
    int insert(Anniversary anniversary);

    /**
     * 根据ID查询纪念日
     */
    Anniversary selectById(@Param("id") Long id);

    /**
     * 根据ID和群组ID查询（校验权限）
     */
    Anniversary selectByIdAndGroupId(@Param("id") Long id, @Param("groupId") Long groupId);

    /**
     * 查询群组的所有纪念日（情侣共享）
     */
    List<Anniversary> selectByGroupId(@Param("groupId") Long groupId);

    /**
     * 更新纪念日
     */
    int update(Anniversary anniversary);

    /**
     * 删除纪念日
     */
    int deleteById(@Param("id") Long id);

    /**
     * 查询所有纪念日（用于定时任务）
     */
    List<Anniversary> selectAll();
}