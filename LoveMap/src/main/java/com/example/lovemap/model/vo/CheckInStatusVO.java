package com.example.lovemap.model.vo;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 积分账户 + 签到日历 VO
 *
 *   points              当前可用积分
 *   totalPoints         历史累计获得
 *   continuousDays      当前连续签到天数
 *   totalCheckInDays    累计签到天数
 *   todayCheckedIn      今天是否已签到
 *   todayScore          今天签到应得积分（若未签则为 0）
 *   month               当前展示月份（YYYY-MM）
 *   checkedDates        当月已签到的日期列表（yyyy-MM-dd）
 *   scoreByDate         当月每天签到获得的积分（map: yyyy-MM-dd -> 积分）
 */
@Data
public class CheckInStatusVO {

    private Integer points;
    private Integer totalPoints;
    private Integer continuousDays;
    private Integer totalCheckInDays;

    private Boolean todayCheckedIn;
    private Integer todayScore;

    private LocalDate month;
    private List<String> checkedDates;
    private java.util.Map<String, Integer> scoreByDate;
}
