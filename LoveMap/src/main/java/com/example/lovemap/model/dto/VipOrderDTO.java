package com.example.lovemap.model.dto;

import com.example.lovemap.common.constant.VipConstant;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * VIP 下单请求 DTO
 */
@Data
public class VipOrderDTO {

    /**
     * 选择的 VIP 档位等级，取值见 {@link VipConstant}
     */
    @NotNull(message = "请选择 VIP 档位")
    @Min(value = VipConstant.LEVEL_WEEK, message = "VIP 档位不合法")
    @Max(value = VipConstant.LEVEL_FOREVER, message = "VIP 档位不合法")
    private Integer level;
}
