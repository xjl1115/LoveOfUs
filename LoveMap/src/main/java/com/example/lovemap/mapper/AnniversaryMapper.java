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
     * 根据ID查询（情侣组数据，或未绑定情侣时创建人为本人的个人数据）
     */
    Anniversary selectByIdAndGroupOrUser(@Param("id") Long id,
                                         @Param("groupId") Long groupId,
                                         @Param("userId") Long userId);

    /**
     * 查询归属当前用户的所有纪念日（情侣组数据 + 未绑定情侣时本人的个人数据）
     */
    List<Anniversary> selectByGroupOrUser(@Param("groupId") Long groupId,
                                          @Param("userId") Long userId);

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
