package com.example.lovemap.service;

import com.example.lovemap.common.Result;
import com.example.lovemap.model.vo.CheckInResultVO;
import com.example.lovemap.model.vo.CheckInStatusVO;

import java.time.YearMonth;

/**
 * 用户积分账户与签到 Service
 */
public interface PointsService {

    /**
     * 获取积分账户 + 当月签到日历
     *
     * @param userId   用户 ID
     * @param month    要查询的月份（null 表示当月）
     */
    Result<CheckInStatusVO> getCheckInStatus(Integer userId, YearMonth month);

    /**
     * 执行每日签到
     *  - 幂等：今天已签到则返回上次结果并不重复加分
     */
    Result<CheckInResultVO> checkIn(Integer userId);

    /**
     * 加积分（带流水），用于任务完成、管理员调整等不影响签到的场景
     *
     * @return 加分后余额
     */
    int addPoints(Long userId, int delta, int changeType, String refType, Long refId, String remark);

    /**
     * 回退积分（带负向流水），用于取消任务等场景
     *
     * @return 回退后余额
     */
    int refundPoints(Long userId, int delta, String refType, Long refId, String remark);
}
