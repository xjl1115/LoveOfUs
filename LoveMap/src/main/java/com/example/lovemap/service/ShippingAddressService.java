package com.example.lovemap.service;

import com.example.lovemap.common.Result;
import com.example.lovemap.model.dto.ShippingAddressDTO;
import com.example.lovemap.model.entity.ShippingAddress;

import java.util.List;

/**
 * 收货地址 Service
 */
public interface ShippingAddressService {

    /** 当前用户全部地址（默认地址优先） */
    Result<List<ShippingAddress>> list(Integer userId);

    /** 新增地址 */
    Result<ShippingAddress> create(Integer userId, ShippingAddressDTO dto);

    /** 修改地址 */
    Result<ShippingAddress> update(Integer userId, Long id, ShippingAddressDTO dto);

    /** 删除地址 */
    Result<Void> delete(Integer userId, Long id);

    /** 设为默认地址 */
    Result<Void> setDefault(Integer userId, Long id);
}