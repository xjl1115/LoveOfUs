package com.example.lovemap.controller;

import com.example.lovemap.common.Result;
import com.example.lovemap.model.vo.CheckInResultVO;
import com.example.lovemap.model.vo.CheckInStatusVO;
import com.example.lovemap.service.PointsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;

/**
 * 积分账户与签到 Controller（用户端）
 */
@RestController
@Tag(name = "积分签到", description = "每日签到 / 积分账户")
@RequestMapping("/points")
@RequiredArgsConstructor
public class PointsController {

    private final PointsService pointsService;

    /**
     * 获取签到状态 + 当月日历
     * month=2026-10；不传则为当月
     */
    @GetMapping("/check-in")
    @Operation(summary = "获取签到状态与当月日历")
    public Result<CheckInStatusVO> checkInStatus(@RequestAttribute("userId") Integer userId,
                                                  @RequestParam(value = "month", required = false) String month) {
        YearMonth ym = month == null || month.isBlank() ? null : YearMonth.parse(month);
        return pointsService.getCheckInStatus(userId, ym);
    }

    /**
     * 执行签到（幂等：今天已签则返回上次结果）
     */
    @PostMapping("/check-in")
    @Operation(summary = "执行每日签到")
    public Result<CheckInResultVO> checkIn(@RequestAttribute("userId") Integer userId) {
        return pointsService.checkIn(userId);
    }
}
