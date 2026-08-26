package com.example.lovemap.ai.controller;

import com.example.lovemap.ai.dto.AiRenameRequest;
import com.example.lovemap.ai.service.AiSessionService;
import com.example.lovemap.ai.vo.AiSessionDetailVO;
import com.example.lovemap.ai.vo.AiSessionSummaryVO;
import com.example.lovemap.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * AI 会话 Controller
 */
@Slf4j
@RestController
@RequestMapping("/ai/sessions")
@RequiredArgsConstructor
@Tag(name = "AI 会话管理")
public class AiSessionController {

    private final AiSessionService aiSessionService;

    /**
     * 会话列表（当前用户）
     */
    @GetMapping
    @Operation(summary = "AI 会话列表")
    public Result<List<AiSessionSummaryVO>> list(HttpServletRequest req) {
        Integer userId = requireUserId(req);
        if (userId == null) return Result.unauthorized("未登录");
        return aiSessionService.listSessions(userId);
    }

    /**
     * 会话详情（含消息列表）
     */
    @GetMapping("/{sessionId}")
    @Operation(summary = "AI 会话详情")
    public Result<AiSessionDetailVO> detail(@PathVariable String sessionId, HttpServletRequest req) {
        Integer userId = requireUserId(req);
        if (userId == null) return Result.unauthorized("未登录");
        return aiSessionService.getSessionDetail(userId, sessionId);
    }

    /**
     * 重命名
     */
    @PutMapping("/{sessionId}/title")
    @Operation(summary = "重命名 AI 会话")
    public Result<AiSessionSummaryVO> rename(@PathVariable String sessionId,
                                              @Valid @RequestBody AiRenameRequest body,
                                              HttpServletRequest req) {
        Integer userId = requireUserId(req);
        if (userId == null) return Result.unauthorized("未登录");
        return aiSessionService.renameSession(userId, sessionId, body);
    }

    /**
     * 删除（级联删消息）
     */
    @DeleteMapping("/{sessionId}")
    @Operation(summary = "删除 AI 会话")
    public Result<Void> delete(@PathVariable String sessionId, HttpServletRequest req) {
        Integer userId = requireUserId(req);
        if (userId == null) return Result.unauthorized("未登录");
        return aiSessionService.deleteSession(userId, sessionId);
    }

    private Integer requireUserId(HttpServletRequest req) {
        Object obj = req.getAttribute("userId");
        if (obj instanceof Long l) return l.intValue();
        if (obj instanceof Integer i) return i;
        if (obj != null) {
            try { return Integer.parseInt(obj.toString()); } catch (Exception ignore) {}
        }
        return null;
    }
}