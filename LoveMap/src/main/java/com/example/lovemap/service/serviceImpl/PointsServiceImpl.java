package com.example.lovemap.service.serviceImpl;

import com.example.lovemap.common.BusinessException;
import com.example.lovemap.common.Result;
import com.example.lovemap.common.ResultCode;
import com.example.lovemap.common.constant.PointsConstant;
import com.example.lovemap.mapper.UserMapper;
import com.example.lovemap.mapper.UserPointsLogMapper;
import com.example.lovemap.mapper.UserPointsMapper;
import com.example.lovemap.model.entity.User;
import com.example.lovemap.model.entity.UserPoints;
import com.example.lovemap.model.entity.UserPointsLog;
import com.example.lovemap.model.vo.CheckInResultVO;
import com.example.lovemap.model.vo.CheckInStatusVO;
import com.example.lovemap.service.PointsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 积分账户与签到实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PointsServiceImpl implements PointsService {

    private final UserPointsMapper userPointsMapper;
    private final UserPointsLogMapper userPointsLogMapper;
    private final UserMapper userMapper;

    @Override
    public Result<CheckInStatusVO> getCheckInStatus(Integer userId, YearMonth month) {
        User user = requireUser(userId);
        ensureAccount(user.getId());

        YearMonth ym = month != null ? month : YearMonth.now();
        LocalDate start = ym.atDay(1);
        LocalDate end = ym.atEndOfMonth();
        LocalDate today = LocalDate.now();

        UserPoints account = userPointsMapper.selectByUserId(user.getId());
        if (account == null) {
            account = newEmpty(user.getId());
        }

        List<String> checkedDates = userPointsLogMapper.checkInDatesByMonth(user.getId(), start, end);
        List<Map<String, Object>> dailyRows = userPointsLogMapper.dailyScoreByMonth(user.getId(), start, end);
        Map<String, Integer> scoreByDate = new HashMap<>();
        for (Map<String, Object> row : dailyRows) {
            Object day = row.get("day");
            Object score = row.get("score");
            if (day != null && score != null) {
                scoreByDate.put(day.toString(), ((Number) score).intValue());
            }
        }

        boolean todayChecked = account.getLastCheckInAt() != null && account.getLastCheckInAt().equals(today);

        CheckInStatusVO vo = new CheckInStatusVO();
        vo.setPoints(account.getPoints());
        vo.setTotalPoints(account.getTotalPoints());
        vo.setContinuousDays(account.getContinuousDays());
        vo.setTotalCheckInDays(account.getTotalCheckInDays());
        vo.setTodayCheckedIn(todayChecked);
        vo.setTodayScore(todayChecked
                ? scoreByDate.getOrDefault(today.toString(), PointsConstant.computeCheckInScore(account.getContinuousDays()))
                : 0);
        vo.setMonth(LocalDate.of(ym.getYear(), ym.getMonthValue(), 1));
        vo.setCheckedDates(checkedDates);
        vo.setScoreByDate(scoreByDate);
        return Result.success(vo);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<CheckInResultVO> checkIn(Integer userId) {
        User user = requireUser(userId);
        ensureAccount(user.getId());

        LocalDate today = LocalDate.now();
        UserPoints account = userPointsMapper.selectByUserId(user.getId());
        if (account == null) {
            throw new BusinessException(ResultCode.INTERNAL_SERVER_ERROR, "积分账户异常");
        }

        // 幂等：今天已签到
        if (account.getLastCheckInAt() != null && account.getLastCheckInAt().equals(today)) {
            CheckInResultVO vo = new CheckInResultVO();
            int continuous = account.getContinuousDays() != null ? account.getContinuousDays() : 0;
            vo.setScore(PointsConstant.computeCheckInScore(continuous));
            vo.setContinuousDays(continuous);
            vo.setCurrentPoints(account.getPoints());
            vo.setIsMilestone(false);
            return Result.success("今天已签到", vo);
        }

        int newContinuous;
        if (account.getLastCheckInAt() != null && account.getLastCheckInAt().equals(today.minusDays(1))) {
            newContinuous = (account.getContinuousDays() == null ? 0 : account.getContinuousDays()) + 1;
        } else {
            newContinuous = 1;
        }
        int newTotal = (account.getTotalCheckInDays() == null ? 0 : account.getTotalCheckInDays()) + 1;
        int score = PointsConstant.computeCheckInScore(newContinuous);
        boolean isMilestone = false;
        for (int d : PointsConstant.MILESTONE_DAYS) {
            if (d == newContinuous) {
                isMilestone = true;
                break;
            }
        }

        int affected = userPointsMapper.incrPoints(user.getId(), score, newContinuous, newTotal, today);
        if (affected != 1) {
            throw new BusinessException(ResultCode.INTERNAL_SERVER_ERROR, "签到失败，请重试");
        }

        int newBalance = (account.getPoints() == null ? 0 : account.getPoints()) + score;
        UserPointsLog logRow = new UserPointsLog();
        logRow.setUserId(user.getId());
        logRow.setChangeType(PointsConstant.CHANGE_TYPE_CHECKIN);
        logRow.setChangePoints(score);
        logRow.setBalanceAfter(newBalance);
        logRow.setRefType(PointsConstant.REF_CHECKIN);
        logRow.setRefId(null);
        logRow.setRemark(isMilestone
                ? "连续签到 " + newContinuous + " 天，里程碑奖励"
                : "连续签到 " + newContinuous + " 天");
        userPointsLogMapper.insert(logRow);

        CheckInResultVO vo = new CheckInResultVO();
        vo.setScore(score);
        vo.setContinuousDays(newContinuous);
        vo.setCurrentPoints(newBalance);
        vo.setIsMilestone(isMilestone);

        log.info("[签到] userId={} continuousDays={} score={} milestone={}",
                user.getId(), newContinuous, score, isMilestone);
        return Result.success(isMilestone ? "里程碑奖励！" : "签到成功", vo);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int addPoints(Long userId, int delta, int changeType, String refType, Long refId, String remark) {
        if (delta <= 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "积分变动必须为正数");
        }
        ensureAccount(userId);
        userPointsMapper.addPointsOnly(userId, delta);

        UserPoints account = userPointsMapper.selectByUserId(userId);
        int balance = account.getPoints();

        writeLog(userId, changeType, delta, balance, refType, refId, remark);
        return balance;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int refundPoints(Long userId, int delta, String refType, Long refId, String remark) {
        if (delta >= 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "回退积分必须为负数");
        }
        ensureAccount(userId);
        int affected = userPointsMapper.subtractPointsOnly(userId, delta);
        if (affected != 1) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "积分不足，无法回退");
        }

        UserPoints account = userPointsMapper.selectByUserId(userId);
        int balance = account.getPoints();

        writeLog(userId, PointsConstant.CHANGE_TYPE_TASK, delta, balance, refType, refId, remark);
        return balance;
    }

    private void writeLog(Long userId, int changeType, int changePoints, int balanceAfter,
                          String refType, Long refId, String remark) {
        UserPointsLog logRow = new UserPointsLog();
        logRow.setUserId(userId);
        logRow.setChangeType(changeType);
        logRow.setChangePoints(changePoints);
        logRow.setBalanceAfter(balanceAfter);
        logRow.setRefType(refType);
        logRow.setRefId(refId);
        logRow.setRemark(remark);
        userPointsLogMapper.insert(logRow);
    }

    // ==================== 私有 ====================

    private User requireUser(Integer userId) {
        User u = userMapper.selectById(userId);
        if (u == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "用户不存在");
        }
        return u;
    }

    private void ensureAccount(Long userId) {
        if (userPointsMapper.selectByUserId(userId) == null) {
            userPointsMapper.insertIgnore(userId);
        }
    }

    private UserPoints newEmpty(Long userId) {
        UserPoints p = new UserPoints();
        p.setUserId(userId);
        p.setPoints(0);
        p.setTotalPoints(0);
        p.setContinuousDays(0);
        p.setTotalCheckInDays(0);
        return p;
    }
}
