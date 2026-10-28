package com.example.lovemap.controller;

import com.example.lovemap.common.Result;
import com.example.lovemap.model.dto.ShippingAddressDTO;
import com.example.lovemap.model.entity.ShippingAddress;
import com.example.lovemap.service.ShippingAddressService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 收货地址 Controller（用户端）
 */
@RestController
@Tag(name = "收货地址", description = "用户收货地址管理")
@RequestMapping("/shipping-addresses")
@RequiredArgsConstructor
public class ShippingAddressController {

    private final ShippingAddressService shippingAddressService;

    /** 列出当前用户全部地址 */
    @GetMapping
    @Operation(summary = "我的收货地址列表")
    public Result<List<ShippingAddress>> list(@RequestAttribute("userId") Integer userId) {
        return shippingAddressService.list(userId);
    }

    /** 新增地址 */
    @PostMapping
    @Operation(summary = "新增收货地址")
    public Result<ShippingAddress> create(@RequestAttribute("userId") Integer userId,
                                          @Valid @RequestBody ShippingAddressDTO dto) {
        return shippingAddressService.create(userId, dto);
    }

    /** 修改地址 */
    @PutMapping("/{id}")
    @Operation(summary = "修改收货地址")
    public Result<ShippingAddress> update(@RequestAttribute("userId") Integer userId,
                                          @PathVariable Long id,
                                          @Valid @RequestBody ShippingAddressDTO dto) {
        return shippingAddressService.update(userId, id, dto);
    }

    /** 删除地址 */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除收货地址")
    public Result<Void> delete(@RequestAttribute("userId") Integer userId,
                               @PathVariable Long id) {
        return shippingAddressService.delete(userId, id);
    }

    /** 设为默认地址 */
    @PutMapping("/{id}/default")
    @Operation(summary = "设为默认收货地址")
    public Result<Void> setDefault(@RequestAttribute("userId") Integer userId,
                                   @PathVariable Long id) {
        return shippingAddressService.setDefault(userId, id);
    }
}