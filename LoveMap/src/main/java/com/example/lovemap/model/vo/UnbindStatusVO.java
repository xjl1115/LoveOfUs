package com.example.lovemap.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 解除绑定状态VO
 */
@Data
public class UnbindStatusVO {

    /**
     * 是否正在申请解除绑定
     */
    private Boolean requesting;

    /**
     * 解除生效日期
     */
    private LocalDateTime effectiveDate;

    /**
     * 剩余冷静期天数
     */
    private Integer cooldownDays;
}
