package com.example.lovemap.mapper;

import com.example.lovemap.model.entity.ThingsUrlRel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 事项与图片关联 Mapper
 */
@Mapper
public interface ThingsUrlRelMapper {

    /**
     * 批量插入
     */
    int batchInsert(@Param("list") List<ThingsUrlRel> list);

    /**
     * 根据事项ID查询所有关联
     */
    List<ThingsUrlRel> selectByThingId(@Param("thingId") Long thingId);

    /**
     * 根据事项ID查询（情侣组记录，或未绑定情侣时上传人为本人的个人记录）
     */
    List<ThingsUrlRel> selectByThingIdAndGroupOrUser(@Param("thingId") Long thingId,
                                                     @Param("groupId") Long groupId,
                                                     @Param("userId") Long userId);

    /**
     * 查询所有关联（情侣组 + 未绑定情侣时本人的个人记录）
     */
    List<ThingsUrlRel> selectByGroupOrUser(@Param("groupId") Long groupId,
                                           @Param("userId") Long userId);

    /**
     * 根据URL ID 删除关联
     */
    int deleteByUrlId(@Param("urlId") Long urlId);

    /**
     * 根据事项ID删除所有关联
     */
    int deleteByThingId(@Param("thingId") Long thingId);
}
