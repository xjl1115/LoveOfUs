package com.example.lovemap.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 解除绑定结果VO
 */
@Data
public class UnbindResultVO {

    /**
     * 解除生效日期
     */
    private LocalDateTime effectiveDate;

    /**
     * 冷静期天数
     */
    private Integer cooldownDays;
}
