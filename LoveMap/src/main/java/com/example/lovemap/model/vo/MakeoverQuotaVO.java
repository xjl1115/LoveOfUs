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
     * 总额度：本月免费次数 + VIP 档位额外次数 + 未用完的次卡次数；不限量时为 null
     */
    private Integer total;

    /**
     * 本月已使用的月度基础额度次数（次卡消耗不计入）
     */
    private Integer used;

    /**
     * 剩余次数 = 本月基础额度剩余 + 次卡剩余（次卡不过期）；不限量时为 null
     */
    private Integer remaining;
}
