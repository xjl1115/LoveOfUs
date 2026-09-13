package com.example.lovemap.ai.controller;

import com.example.lovemap.ai.context.AiUserContext;
import com.example.lovemap.ai.dto.ChatRequest;
import com.example.lovemap.ai.dto.ChatResponse;
import com.example.lovemap.ai.exception.AiErrorMessages;
import com.example.lovemap.ai.service.AiChatService;
import com.example.lovemap.common.Result;
import com.example.lovemap.mapper.UserMapper;
import com.example.lovemap.model.entity.User;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.HashMap;
import java.util.Map;

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
    private final UserMapper userMapper;
    private final ObjectMapper objectMapper;

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

        try {
            aiChatService.chatStream(
                    request,
                    chunk -> writeJson(emitter, "chunk", chunkPayload(chunk)),
                    (full, images) -> {
                        // done 帧：附带 AI 答复 + 本轮所有工具返回的图片（供前端气泡以缩略图形式展示）
                        Map<String, Object> payload = new HashMap<>();
                        payload.put("textLen", full == null ? 0 : full.length());
                        if (images != null && !images.isEmpty()) {
                            payload.put("images", images);
                        }
                        writeJson(emitter, "done", payload);
                        emitter.complete();
                        AiUserContext.clear();
                    },
                    error -> {
                        log.error("AI 流式响应出错", error);
                        // 额度不足等异常转成中文文案后再推给前端
                        writeJson(emitter, "error", errorPayload(AiErrorMessages.toUserMessage(error)));
                        // 用 complete() 而非 completeWithError()：错误已通过 error 帧告知前端，
                        // 后者会触发容器异常派发，而此时响应已是 text/event-stream，无法再写 JSON。
                        emitter.complete();
                        AiUserContext.clear();
                    }
            );
        } catch (Exception e) {
            // 兜底：流式启动阶段同步抛出异常（如额度不足）时，同样以 error 帧告知前端
            log.error("AI 流式响应启动失败", e);
            writeJson(emitter, "error", errorPayload(AiErrorMessages.toUserMessage(e)));
            emitter.complete();
            AiUserContext.clear();
            return emitter;
        }

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
        // 真实 groupId：从 user 表反查（group_id 是情侣共享的，工具中需要它做正确隔离）
        Long groupId = userId; // fallback：未查到时用 userId 兜底
        try {
            User me = userMapper.selectById(userId.intValue());
            if (me != null && me.getGroupId() != null) {
                groupId = me.getGroupId();
            }
        } catch (Exception e) {
            log.warn("[AI] bindContext 反查 groupId 失败 userId={}", userId, e);
        }
        AiUserContext.set(userId, groupId);
    }

    private void write(SseEmitter emitter, String event, String data) {
        try {
            // 不再带 MediaType.APPLICATION_JSON，避免 Spring 二次 JSON 包裹
            // 触发前端 "Expected property name or Y in JSON" (position 26) 解析错误。
            emitter.send(SseEmitter.event().name(event).data(data));
        } catch (IOException | IllegalStateException e) {
            log.debug("SSE 写入失败（客户端可能已断开）: {}", e.getMessage());
        }
    }

    /**
     * 用统一的 ObjectMapper 序列化数据对象为 JSON 字符串后再写入 SSE。
     * <p>
     * 与 {@link #write(SseEmitter, String, String)} 配合，data 字段已是合法 JSON 字符串，
     * 不要再加 MediaType.APPLICATION_JSON（否则 Spring 会再包一层）。
     */
    private void writeJson(SseEmitter emitter, String event, Object payload) {
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.warn("[AI] SSE 序列化失败 event={}", event, e);
            return;
        }
        write(emitter, event, json);
    }

    private Map<String, Object> chunkPayload(String chunk) {
        Map<String, Object> map = new HashMap<>(2);
        map.put("text", chunk == null ? "" : chunk);
        return map;
    }

    private Map<String, Object> errorPayload(String message) {
        Map<String, Object> map = new HashMap<>(2);
        map.put("message", message == null ? "" : message);
        return map;
    }

    private String toJsonArray(java.util.List<?> list) {
        try {
            return objectMapper.writeValueAsString(list);
        } catch (Exception e) {
            log.warn("序列化图片列表失败", e);
            return "[]";
        }
    }
}
