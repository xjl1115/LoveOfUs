package com.example.lovemap.ai.service;

import com.example.lovemap.ai.dto.ChatRequest;
import com.example.lovemap.ai.dto.ChatResponse;
import com.example.lovemap.ai.exception.AiDisabledException;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * AI 聊天服务（仅 LLM 调用，不含 Tools）
 * <p>
 * 使用 ObjectProvider 安全注入 ChatModel Bean（未配置 API Key 时为 null）。
 * 当前阶段不持久化历史会话：前端 localStorage 维持上下文；后续可叠加 ChatMemory。
 */
@Slf4j
@Service
public class AiChatService {

    private final ObjectProvider<ChatModel> chatModelProvider;
    private final ObjectProvider<StreamingChatModel> streamingChatModelProvider;

    public AiChatService(ObjectProvider<ChatModel> chatModelProvider,
                          ObjectProvider<StreamingChatModel> streamingChatModelProvider) {
        this.chatModelProvider = chatModelProvider;
        this.streamingChatModelProvider = streamingChatModelProvider;
    }

    // ==================== 非流式 ====================

    /**
     * 单轮非流式对话
     */
    public ChatResponse chat(ChatRequest request) {
        String userText = safeText(request.getMessage());
        ChatModel chatModel = requireChatModel();
        log.info("AI chat (non-stream) session={}, text-len={}", request.getSessionId(), userText.length());

        dev.langchain4j.model.chat.response.ChatResponse lcResponse =
                chatModel.chat(UserMessage.from(userText));
        String aiText = lcResponse.aiMessage().text();

        return ChatResponse.builder()
                .sessionId(request.getSessionId())
                .message(ChatResponse.ChatMessage.builder()
                        .id(UUID.randomUUID().toString())
                        .role("ai")
                        .content(aiText)
                        .createdAt(System.currentTimeMillis())
                        .build())
                .build();
    }

    // ==================== 流式 ====================

    /**
     * 流式对话（SSE 用）
     */
    public void chatStream(ChatRequest request,
                           Consumer<String> onChunk,
                           Consumer<String> onComplete,
                           Consumer<Throwable> onError) {
        StreamingChatModel streamingChatModel;
        try {
            streamingChatModel = requireStreamingChatModel();
        } catch (AiDisabledException e) {
            onError.accept(e);
            return;
        }

        String userText = safeText(request.getMessage());
        log.info("AI chat (stream) session={}, text-len={}", request.getSessionId(), userText.length());

        StringBuilder fullText = new StringBuilder();

        StreamingChatResponseHandler handler = new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
                fullText.append(partialResponse);
                try {
                    onChunk.accept(partialResponse);
                } catch (Exception e) {
                    log.warn("onChunk 回调异常", e);
                }
            }

            @Override
            public void onCompleteResponse(dev.langchain4j.model.chat.response.ChatResponse response) {
                log.debug("AI stream complete, total-len={}", fullText.length());
                try {
                    onComplete.accept(fullText.toString());
                } catch (Exception e) {
                    log.warn("onComplete 回调异常", e);
                }
            }

            @Override
            public void onError(Throwable error) {
                log.error("AI stream error", error);
                try {
                    onError.accept(error);
                } catch (Exception e) {
                    log.warn("onError 回调异常", e);
                }
            }
        };

        try {
            // 1.18 API：StreamingChatModel.chat(String | List<ChatMessage>, handler)
            // 这里直接传文本，最简形态
            streamingChatModel.chat(userText, handler);
        } catch (Exception e) {
            onError.accept(e);
        }
    }

    // ==================== 工具 ====================

    private ChatModel requireChatModel() {
        ChatModel model = chatModelProvider.getIfAvailable();
        if (model == null) {
            throw new AiDisabledException("AI 服务未启用或未配置 API Key");
        }
        return model;
    }

    private StreamingChatModel requireStreamingChatModel() {
        StreamingChatModel model = streamingChatModelProvider.getIfAvailable();
        if (model == null) {
            throw new AiDisabledException("AI 服务未启用或未配置 API Key");
        }
        return model;
    }

    private String safeText(String text) {
        if (text == null) return "";
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("消息内容不能为空");
        }
        if (trimmed.length() > 2000) {
            return trimmed.substring(0, 2000);
        }
        return trimmed;
    }
}