package com.example.lovemap.model.vo;

import lombok.Data;

/**
 * 签到结果 VO
 */
@Data
public class CheckInResultVO {

    /** 本次获得的积分 */
    private Integer score;

    /** 连续签到天数 */
    private Integer continuousDays;

    /** 当前总积分 */
    private Integer currentPoints;

    /** 是否命中里程碑奖励 */
    private Boolean isMilestone;
}
