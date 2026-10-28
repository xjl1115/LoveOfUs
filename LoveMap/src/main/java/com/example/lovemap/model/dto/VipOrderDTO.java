package com.example.lovemap.model.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.math.BigDecimal;

/**
 * VIP / 次卡下单请求 DTO
 * <p>
 * orderType=1（VIP 卡）：必填 level；下单后待顾问收款并开通。
 * orderType=2（次卡）：必填 quotaType + quotaCount；下单后待顾问收款，开通后按次数写入 quota_log。
 * <p>
 * unitPriceYuan / totalPriceYuan 由前端按展示价提交，后端校验与标准价一致，
 * 既让前端负载完整（单价 + 总价），又防止被篡改。
 */
@Data
public class VipOrderDTO {

    /** 订单类型：1=VIP 卡，2=次卡 */
    @Min(value = 1, message = "订单类型不合法")
    @Max(value = 2, message = "订单类型不合法")
    private Integer orderType;

    /** VIP 档位等级（orderType=1 时必填） */
    @Min(value = 1, message = "VIP 档位不合法")
    @Max(value = 5, message = "VIP 档位不合法")
    private Integer level;

    /** 次卡对应的配额类型（orderType=2 时必填）：1=AI化妆 2=AI约会 3=AI心愿 */
    @Min(value = 1, message = "次卡配额类型不合法")
    @Max(value = 3, message = "次卡配额类型不合法")
    private Integer quotaType;

    /** 次卡购买的次数（orderType=2 时必填，1-999） */
    @Min(value = 1, message = "次数至少为 1")
    @Max(value = 999, message = "次数不能超过 999")
    private Integer quotaCount;

    /**
     * 存在待开通订单时，用户在前端点击"确认继续下单"后置为 true，后端才会取消旧订单并创建新订单。
     */
    private Boolean confirmOldOrder;

    /**
     * 次卡单价（orderType=2 时由前端提交，VIP 卡下单时为 null）。
     * 后端会用 VipConstant.quotaUnitPriceYuan 校验一致性，避免前端被篡改。
     */
    @DecimalMin(value = "0.00", inclusive = false, message = "次卡单价不合法")
    private BigDecimal unitPriceYuan;

    /**
     * 订单总价（由前端提交，等于 单价 × 次数 或 VIP 档位价）。
     * 后端会比对自身计算结果，不一致时拒绝下单。
     */
    @DecimalMin(value = "0.00", inclusive = false, message = "订单总价不合法")
    private BigDecimal totalPriceYuan;
}
