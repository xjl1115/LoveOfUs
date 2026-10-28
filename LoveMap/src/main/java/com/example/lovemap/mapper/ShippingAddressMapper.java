package com.example.lovemap.mapper;

import com.example.lovemap.model.entity.ShippingAddress;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 收货地址 Mapper
 */
@Mapper
public interface ShippingAddressMapper {

    /** 插入 */
    int insert(ShippingAddress address);

    /** 按主键查询 */
    ShippingAddress selectById(@Param("id") Long id);

    /** 当前用户全部地址（默认地址优先） */
    List<ShippingAddress> selectByUser(@Param("userId") Long userId);

    /** 更新 */
    int update(ShippingAddress address);

    /** 按主键删除（调用方保证归属校验） */
    int deleteById(@Param("id") Long id);

    /** 清除用户某地址外的默认标记（置为 0） */
    int clearDefault(@Param("userId") Long userId);

    /** 将指定地址设为默认 */
    int setDefault(@Param("id") Long id);
}