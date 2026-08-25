package com.example.lovemap.ai.controller;

import com.example.lovemap.ai.dto.ChatRequest;
import com.example.lovemap.ai.dto.ChatResponse;
import com.example.lovemap.ai.service.AiChatService;
import com.example.lovemap.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
    public Result<ChatResponse> chat(@RequestBody ChatRequest request) {
        return Result.success(aiChatService.chat(request));
    }

    /**
     * 流式接口（SSE）
     * <p>
     * 注意：必须设置 produces=text/event-stream，否则 Spring MVC 默认按 JSON 输出
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "AI 聊天（流式）")
    public SseEmitter chatStream(@RequestBody ChatRequest request) {
        // 超时：30 分钟（与现有 SSE 配置一致）
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);

        // 异步回调，避免阻塞 controller 线程
        aiChatService.chatStream(
                request,
                chunk -> write(emitter, "chunk", "{\"text\":\"" + escapeJson(chunk) + "\"}"),
                full -> {
                    write(emitter, "done", "{}");
                    emitter.complete();
                },
                error -> {
                    log.error("AI 流式响应出错", error);
                    write(emitter, "error", "{\"message\":\"" + escapeJson(error.getMessage()) + "\"}");
                    emitter.completeWithError(error);
                }
        );

        // 客户端断开时尝试结束
        emitter.onCompletion(() -> log.debug("SSE emitter completed, session={}", request.getSessionId()));
        emitter.onTimeout(() -> {
            log.warn("SSE emitter timeout, session={}", request.getSessionId());
            emitter.complete();
        });

        return emitter;
    }

    /**
     * 写入一个 SSE 帧
     */
    private void write(SseEmitter emitter, String event, String data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data, MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException e) {
            // 客户端已断开，常见异常，不打 ERROR
            log.debug("SSE 写入失败（客户端可能已断开）: {}", e.getMessage());
        }
    }

    /**
     * 简单 JSON 字符串转义（防 LLM 输出含特殊字符破坏 SSE）
     */
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
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }
}