package com.example.lovemap.mapper;

import com.example.lovemap.model.entity.ThingsCompletion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 事项完成记录 Mapper
 */
@Mapper
public interface ThingsCompletionMapper {

    /**
     * 插入完成记录（情侣共享：同一 thingId+groupId 唯一）
     */
    int insert(ThingsCompletion completion);

    /**
     * 根据事项ID查询（情侣组记录，或未绑定情侣时完成人为本人的个人记录）
     */
    ThingsCompletion selectByThingAndGroupOrUser(@Param("thingId") Long thingId,
                                                 @Param("groupId") Long groupId,
                                                 @Param("userId") Long userId);

    /**
     * 查询所有完成记录（情侣组 + 未绑定情侣时本人的个人记录）
     */
    List<ThingsCompletion> selectByGroupOrUser(@Param("groupId") Long groupId,
                                               @Param("userId") Long userId);

    /**
     * 已完成事项数（情侣组 + 未绑定情侣时本人的个人记录）
     */
    long countAchievedByGroupOrUser(@Param("groupId") Long groupId,
                                    @Param("userId") Long userId);

    /**
     * 删除（取消已完成）
     */
    int deleteByThingAndGroupOrUser(@Param("thingId") Long thingId,
                                    @Param("groupId") Long groupId,
                                    @Param("userId") Long userId);

    /**
     * 重置群组下所有完成记录
     */
    int deleteByGroupId(@Param("groupId") Long groupId);
}
