package com.example.lovemap.service.serviceImpl;

import com.example.lovemap.common.BusinessException;
import com.example.lovemap.common.Result;
import com.example.lovemap.common.ResultCode;
import com.example.lovemap.mapper.ShippingAddressMapper;
import com.example.lovemap.model.dto.ShippingAddressDTO;
import com.example.lovemap.model.entity.ShippingAddress;
import com.example.lovemap.service.ShippingAddressService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 收货地址实现
 *
 * 默认地址规则：
 *   - 首次新增自动设为默认
 *   - 手动勾选默认时先清除其它默认标记，保证用户至多一个默认地址
 */
@Service
@RequiredArgsConstructor
public class ShippingAddressServiceImpl implements ShippingAddressService {

    private final ShippingAddressMapper addressMapper;

    @Override
    public Result<List<ShippingAddress>> list(Integer userId) {
        return Result.success(addressMapper.selectByUser(userId.longValue()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<ShippingAddress> create(Integer userId, ShippingAddressDTO dto) {
        boolean makeDefault = (dto.getIsDefault() != null && dto.getIsDefault() == 1)
                || addressMapper.selectByUser(userId.longValue()).isEmpty();
        if (makeDefault) {
            addressMapper.clearDefault(userId.longValue());
        }

        ShippingAddress address = build(new ShippingAddress(), userId, dto, makeDefault);
        addressMapper.insert(address);
        return Result.success(address);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<ShippingAddress> update(Integer userId, Long id, ShippingAddressDTO dto) {
        ShippingAddress existing = requireOwnedAddress(userId, id);
        boolean makeDefault = dto.getIsDefault() != null && dto.getIsDefault() == 1;
        if (makeDefault) {
            addressMapper.clearDefault(userId.longValue());
        } else {
            // 未勾选默认时沿用原有默认标记，避免编辑默认地址后丢失默认状态
            makeDefault = existing.getIsDefault() != null && existing.getIsDefault() == 1;
        }

        ShippingAddress address = build(existing, userId, dto, makeDefault);
        addressMapper.update(address);
        return Result.success(address);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> delete(Integer userId, Long id) {
        ShippingAddress existing = requireOwnedAddress(userId, id);
        addressMapper.deleteById(id);

        // 删除的是默认地址时，将最新一条地址提升为默认，保证有地址时必有默认
        if (existing.getIsDefault() != null && existing.getIsDefault() == 1) {
            List<ShippingAddress> remaining = addressMapper.selectByUser(userId.longValue());
            if (!remaining.isEmpty()) {
                addressMapper.setDefault(remaining.get(0).getId());
            }
        }
        return Result.success();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> setDefault(Integer userId, Long id) {
        requireOwnedAddress(userId, id);
        addressMapper.clearDefault(userId.longValue());
        addressMapper.setDefault(id);
        return Result.success();
    }

    private ShippingAddress requireOwnedAddress(Integer userId, Long id) {
        ShippingAddress address = addressMapper.selectById(id);
        if (address == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "地址不存在");
        }
        if (address.getUserId() == null || !address.getUserId().equals(userId.longValue())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权操作该地址");
        }
        return address;
    }

    private ShippingAddress build(ShippingAddress address, Integer userId,
                                  ShippingAddressDTO dto, boolean isDefault) {
        address.setUserId(userId.longValue());
        address.setReceiverName(dto.getReceiverName().trim());
        address.setReceiverPhone(dto.getReceiverPhone().trim());
        address.setProvince(dto.getProvince().trim());
        address.setCity(dto.getCity().trim());
        address.setDetailAddress(dto.getDetailAddress().trim());
        address.setIsDefault(isDefault ? 1 : 0);
        return address;
    }
}