package com.example.lovemap.model.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 领取任务积分请求 DTO
 *
 * 任务由系统自动识别完成（状态变为「已完成待领取」），
 * 用户点击「领取积分」后调用本接口，状态变为「已领取」并发放积分。
 */
@Data
public class CoupleTaskClaimDTO {

    /** 任务 ID */
    @NotNull(message = "任务 ID 不能为空")
    private Long taskId;
}
