package com.example.lovemap.ai.controller;

import com.example.lovemap.ai.context.AiUserContext;
import com.example.lovemap.ai.dto.ChatRequest;
import com.example.lovemap.ai.dto.ChatResponse;
import com.example.lovemap.ai.service.AiChatService;
import com.example.lovemap.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

/**
 * AI 聊天 Controller
 * <p>
 * 接口约定（与前端 chatStream 对齐）：
 * - POST /api/ai/chat           非流式，返回完整 AI 回复
 * - POST /api/ai/chat/stream    SSE 流式，逐 chunk 返回 text
 *   帧格式：
 *     event: chunk
 *     data: {"text":"..."}
 *
 *     event: done
 *     data: {}
 *
 *     event: error
 *     data: {"message":"..."}
 */
@Slf4j
@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
@Tag(name = "AI 聊天")
public class AiChatController {

    private final AiChatService aiChatService;

    /**
     * 非流式接口（前端降级用）
     */
    @PostMapping("/chat")
    @Operation(summary = "AI 聊天（非流式）")
    public Result<ChatResponse> chat(@RequestBody ChatRequest request, HttpServletRequest httpReq) {
        bindContext(httpReq);
        return Result.success(aiChatService.chat(request));
    }

    /**
     * 流式接口（SSE）
     * <p>
     * 注意：必须设置 produces=text/event-stream，否则 Spring MVC 默认按 JSON 输出
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "AI 聊天（流式）")
    public SseEmitter chatStream(@RequestBody ChatRequest request, HttpServletRequest httpReq) {
        bindContext(httpReq);

        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);

        aiChatService.chatStream(
                request,
                chunk -> write(emitter, "chunk", "{\"text\":\"" + escapeJson(chunk) + "\"}"),
                full -> {
                    write(emitter, "done", "{}");
                    emitter.complete();
                    AiUserContext.clear();
                },
                error -> {
                    log.error("AI 流式响应出错", error);
                    write(emitter, "error", "{\"message\":\"" + escapeJson(error.getMessage()) + "\"}");
                    emitter.completeWithError(error);
                    AiUserContext.clear();
                }
        );

        emitter.onCompletion(() -> log.debug("SSE emitter completed, session={}", request.getSessionId()));
        emitter.onTimeout(() -> {
            log.warn("SSE emitter timeout, session={}", request.getSessionId());
            emitter.complete();
            AiUserContext.clear();
        });

        return emitter;
    }

    /**
     * 将 JwtAuthFilter 已写入的 userId 转成 AiUserContext 工具上下文。
     * groupId 当前未在 JwtAuthFilter 中保存；如果工具需要，后续可补。
     */
    private void bindContext(HttpServletRequest httpReq) {
        Object userIdObj = httpReq.getAttribute("userId");
        Long userId = null;
        if (userIdObj instanceof Long l) {
            userId = l;
        } else if (userIdObj instanceof Integer i) {
            userId = i.longValue();
        } else if (userIdObj != null) {
            try { userId = Long.parseLong(userIdObj.toString()); } catch (Exception ignore) {}
        }
        // groupId 暂用 userId 作占位（工具内部主要读 userId）；后续可在 JwtAuthFilter 一并 set
        AiUserContext.set(userId, userId);
    }

    private void write(SseEmitter emitter, String event, String data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data, MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException e) {
            log.debug("SSE 写入失败（客户端可能已断开）: {}", e.getMessage());
        }
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.toString();
    }
}