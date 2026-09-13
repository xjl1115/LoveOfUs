package com.example.lovemap.mapper;

import com.example.lovemap.model.entity.WishlistItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 心愿清单 Mapper
 */
@Mapper
public interface WishlistItemMapper {

    /**
     * 插入心愿项
     */
    int insert(WishlistItem wishlistItem);

    /**
     * 根据 ID 查询
     */
    WishlistItem selectById(@Param("id") Long id);

    /**
     * 根据 ID 查询（情侣组数据，或未绑定情侣时创建人为本人的个人数据）
     */
    WishlistItem selectByIdAndGroupOrUser(@Param("id") Long id,
                                          @Param("groupId") Long groupId,
                                          @Param("userId") Long userId);

    /**
     * 查询列表（情侣组数据 + 未绑定情侣时本人的个人数据）
     */
    List<WishlistItem> selectByGroupOrUser(@Param("groupId") Long groupId,
                                           @Param("userId") Long userId);

    /**
     * 更新心愿项
     */
    int update(@Param("item") WishlistItem wishlistItem, @Param("userId") Long userId);

    /**
     * 更新状态
     */
    int updateStatus(@Param("id") Long id,
                     @Param("groupId") Long groupId,
                     @Param("userId") Long userId,
                     @Param("status") Integer status);

    /**
     * 原子调整进度（current_value = clamp(current_value + delta, 0, target_value)）
     */
    int adjustProgress(@Param("id") Long id,
                       @Param("groupId") Long groupId,
                       @Param("userId") Long userId,
                       @Param("delta") Integer delta);

    /**
     * 软删除
     */
    int softDelete(@Param("id") Long id,
                   @Param("groupId") Long groupId,
                   @Param("userId") Long userId);
}
