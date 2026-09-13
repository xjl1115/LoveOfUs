package com.example.lovemap.model.vo;

import lombok.Data;

/**
 * AI 化妆建议本月额度 VO
 */
@Data
public class MakeoverQuotaVO {

    /**
     * 是否不限量（永久会员）
     */
    private Boolean unlimited;

    /**
     * 本月总额度：免费次数 + VIP 档位额外次数；不限量时为 null
     */
    private Integer total;

    /**
     * 本月已使用次数
     */
    private Integer used;

    /**
     * 本月剩余次数；不限量时为 null
     */
    private Integer remaining;
}
