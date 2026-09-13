package com.example.lovemap.model.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * VIP 批量开通请求 DTO
 */
@Data
public class VipBatchActivateDTO {

    /**
     * 待开通的订单号列表
     */
    @NotEmpty(message = "请至少提供一个订单号")
    private List<String> orderNos;
}