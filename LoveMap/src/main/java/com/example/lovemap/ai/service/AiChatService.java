package com.example.lovemap.ai.service;

import com.example.lovemap.ai.dto.ChatRequest;
import com.example.lovemap.ai.dto.ChatResponse;
import com.example.lovemap.ai.exception.AiDisabledException;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * AI 聊天服务（仅 LLM 调用，不含 Tools）
 * <p>
 * 使用 ObjectProvider 安全注入 ChatModel Bean（未配置 API Key 时为 null）。
 * 当前阶段不持久化历史会话：前端 localStorage 维持上下文；后续可叠加 ChatMemory。
 * <p>
 * 系统提示词（ai.dashscope.system-prompt）通过构造 SystemMessage 拼接到 messages 首位，
 * 兼容 DashScope QwenChatModelBuilder 不提供 defaultSystemMessage 的限制。
 */
@Slf4j
@Service
public class AiChatService {

    private final ObjectProvider<ChatModel> chatModelProvider;
    private final ObjectProvider<StreamingChatModel> streamingChatModelProvider;

    /**
     * 系统提示词，由 LangChain4jConfig 以 Bean 形式注入；为空则不拼接 SystemMessage
     */
    private final String systemPrompt;

    public AiChatService(ObjectProvider<ChatModel> chatModelProvider,
                          ObjectProvider<StreamingChatModel> streamingChatModelProvider,
                          @Value("${ai.dashscope.system-prompt:}") String systemPrompt) {
        this.chatModelProvider = chatModelProvider;
        this.streamingChatModelProvider = streamingChatModelProvider;
        this.systemPrompt = systemPrompt == null ? "" : systemPrompt;
    }

    // ==================== 非流式 ====================

    /**
     * 单轮非流式对话
     */
    public ChatResponse chat(ChatRequest request) {
        String userText = safeText(request.getMessage());
        ChatModel chatModel = requireChatModel();
        log.info("AI chat (non-stream) session={}, text-len={}, systemPrompt={}",
                request.getSessionId(), userText.length(),
                systemPrompt.isBlank() ? "<none>" : "<configured>");

        List<ChatMessage> messages = buildMessages(userText);

        dev.langchain4j.model.chat.response.ChatResponse lcResponse = chatModel.chat(messages);
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
        log.info("AI chat (stream) session={}, text-len={}, systemPrompt={}",
                request.getSessionId(), userText.length(),
                systemPrompt.isBlank() ? "<none>" : "<configured>");

        List<ChatMessage> messages = buildMessages(userText);
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
            // 1.18 API：StreamingChatModel.chat(List<ChatMessage>, handler)
            streamingChatModel.chat(messages, handler);
        } catch (Exception e) {
            onError.accept(e);
        }
    }

    // ==================== 工具 ====================

    /**
     * 构造 messages 列表：SystemMessage（可选） + UserMessage
     */
    private List<ChatMessage> buildMessages(String userText) {
        List<ChatMessage> messages = new ArrayList<>(2);
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(SystemMessage.from(systemPrompt));
        }
        messages.add(UserMessage.from(userText));
        return messages;
    }

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