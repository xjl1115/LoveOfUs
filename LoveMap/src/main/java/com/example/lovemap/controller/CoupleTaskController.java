package com.example.lovemap.controller;

import com.example.lovemap.common.Result;
import com.example.lovemap.model.dto.CoupleTaskClaimDTO;
import com.example.lovemap.model.vo.CoupleTaskVO;
import com.example.lovemap.service.CoupleTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 情侣任务 Controller（用户端）
 *
 * 任务完成由系统自动识别，前端只负责展示状态与领取积分。
 */
@RestController
@Tag(name = "情侣任务", description = "情侣任务状态查询 / 领取积分")
@RequestMapping("/couple-tasks")
@RequiredArgsConstructor
public class CoupleTaskController {

    private final CoupleTaskService coupleTaskService;

    /** 用户端：列出已启用任务，并返回当前周期的完成状态 */
    @GetMapping
    @Operation(summary = "列出已启用情侣任务及当前状态")
    public Result<List<CoupleTaskVO>> list(@RequestAttribute("userId") Integer userId) {
        return coupleTaskService.listForUser(userId);
    }

    /** 已完成待领取 → 已领取，并发放积分 */
    @PostMapping("/claim")
    @Operation(summary = "领取任务积分")
    public Result<CoupleTaskVO> claim(@RequestAttribute("userId") Integer userId,
                                      @Valid @RequestBody CoupleTaskClaimDTO dto) {
        return coupleTaskService.claim(userId, dto);
    }
}
