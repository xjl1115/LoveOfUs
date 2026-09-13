package com.example.lovemap.controller;

import com.example.lovemap.common.Result;
import com.example.lovemap.model.dto.AiCopyFeedbackDTO;
import com.example.lovemap.service.AiCopyFeedbackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * AI 文案反馈 Controller
 * <p>
 * 提供「采纳 / 拒绝」反馈埋点接口，供前端在用户对 AI 文案做选择时上报，
 * 数据落入 ai_copy_feedback 表，后续可用于 Prompt 优化离线分析。
 */
@RestController
@Slf4j
@Tag(name = "AI 文案反馈", description = "AI 纪念日文案采纳 / 拒绝反馈接口")
@RequestMapping("/ai/copy-feedback")
@RequiredArgsConstructor
public class AiCopyFeedbackController {

    private final AiCopyFeedbackService feedbackService;

    @PostMapping
    @Operation(summary = "提交文案采纳 / 拒绝反馈")
    public Result<Void> submitFeedback(@RequestAttribute("userId") Integer userId,
                                       @Valid @RequestBody AiCopyFeedbackDTO dto) {
        return feedbackService.submitFeedback(userId, dto);
    }

    @GetMapping("/acceptance-stats")
    @Operation(summary = "查询当前用户对各文案风格的采纳率（供 Prompt 优化分析）")
    public Result<List<AiCopyFeedbackService.StyleAcceptanceVO>> getAcceptanceStats(
            @RequestAttribute("userId") Integer userId) {
        return feedbackService.getAcceptanceStats(userId);
    }
}