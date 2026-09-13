package com.example.lovemap.model.vo;

import lombok.Data;

import java.util.List;

/**
 * VIP 档位 VO
 */
@Data
public class VipTierVO {

    /**
     * 等级：1-周卡，2-月卡，3-季卡，4-年卡，5-永久
     */
    private Integer level;

    /**
     * 档位名称
     */
    private String name;

    /**
     * 价格（元）
     */
    private Integer priceYuan;

    /**
     * 时长文案：如 "7天" / "永久"
     */
    private String durationText;

    /**
     * 是否永久卡
     */
    private Boolean permanent;

    /**
     * 该档位权益文案
     */
    private List<String> benefits;
}
