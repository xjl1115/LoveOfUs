package com.example.lovemap.mapper;

import com.example.lovemap.model.entity.DatePlan;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 约会计划 Mapper
 */
@Mapper
public interface DatePlanMapper {

    /**
     * 插入约会计划
     */
    int insert(DatePlan datePlan);

    /**
     * 根据 ID 查询
     */
    DatePlan selectById(@Param("id") Long id);

    /**
     * 根据 ID 查询（情侣组数据，或未绑定情侣时创建人为本人的个人数据）
     */
    DatePlan selectByIdAndGroupOrUser(@Param("id") Long id,
                                      @Param("groupId") Long groupId,
                                      @Param("userId") Long userId);

    /**
     * 查询列表（情侣组数据 + 未绑定情侣时本人的个人数据）
     */
    List<DatePlan> selectByGroupOrUser(@Param("groupId") Long groupId,
                                       @Param("userId") Long userId);

    /**
     * 更新约会计划
     */
    int update(@Param("plan") DatePlan datePlan, @Param("userId") Long userId);

    /**
     * 更新状态
     */
    int updateStatus(@Param("id") Long id,
                     @Param("groupId") Long groupId,
                     @Param("userId") Long userId,
                     @Param("status") Integer status);

    /**
     * 软删除
     */
    int softDelete(@Param("id") Long id,
                   @Param("groupId") Long groupId,
                   @Param("userId") Long userId);
}
