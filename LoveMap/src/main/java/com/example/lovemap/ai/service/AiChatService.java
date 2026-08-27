package com.example.lovemap.ai.service;

import com.example.lovemap.ai.context.AiUserContext;
import com.example.lovemap.ai.dto.ChatRequest;
import com.example.lovemap.ai.dto.ChatResponse;
import com.example.lovemap.ai.exception.AiDisabledException;
import com.example.lovemap.ai.service.AiSessionService;
import com.example.lovemap.ai.tool.SimpleToolExecutor;
import com.example.lovemap.ai.vo.AiMessageVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * AI 聊天服务（LLM + Tools）
 * <p>
 * 工具调用流程（与 LangChain4j 1.18 Low-level API 对齐）：
 * 1. 构造 messages = [System?, User]
 * 2. 构造 ChatRequest，parameters 携带 toolSpecifications
 * 3. ChatModel.chat(ChatRequest)
 * 4. 若 AiMessage 包含 toolExecutionRequests：
 *    - SimpleToolExecutor 执行每一个
 *    - 把结果作为 ToolExecutionResultMessage 追加回 messages
 *    - 再发起一轮 chat，直到 AiMessage 不再要求工具调用
 * 5. 返回最终 AiMessage.text()
 */
@Slf4j
@Service
public class AiChatService {

    private final ObjectProvider<ChatModel> chatModelProvider;
    private final ObjectProvider<StreamingChatModel> streamingChatModelProvider;
    private final List<ToolSpecification> toolSpecifications;
    private final Map<String, Object> toolBeanMap;
    private final ObjectMapper objectMapper;
    private final AiSessionService aiSessionService;
    private final AiShortTermMemoryService shortTermMemory;
    private final String systemPrompt;

    /** Tool loop 最多迭代次数（防止模型进入死循环） */
    private static final int MAX_TOOL_ITERATIONS = 5;

    public AiChatService(ObjectProvider<ChatModel> chatModelProvider,
                          ObjectProvider<StreamingChatModel> streamingChatModelProvider,
                          @Qualifier("aiToolSpecifications") List<ToolSpecification> toolSpecifications,
                          @Qualifier("aiToolBeanMap") Map<String, Object> toolBeanMap,
                          ObjectProvider<ObjectMapper> objectMapperProvider,
                          AiSessionService aiSessionService,
                          AiShortTermMemoryService shortTermMemory,
                          @Value("${ai.dashscope.system-prompt:}") String systemPrompt) {
        this.chatModelProvider = chatModelProvider;
        this.streamingChatModelProvider = streamingChatModelProvider;
        this.toolSpecifications = toolSpecifications;
        this.toolBeanMap = toolBeanMap;
        // Spring 容器中可能存在多个 ObjectMapper（业务 + Redis 等），这里强制取主 ObjectMapper
        ObjectMapper m0 = objectMapperProvider.getIfAvailable();
        this.objectMapper = m0 != null ? m0 : new ObjectMapper();
        this.aiSessionService = aiSessionService;
        this.shortTermMemory = shortTermMemory;
        this.systemPrompt = systemPrompt == null ? "" : systemPrompt;
    }

    // ==================== 非流式 ====================

    public ChatResponse chat(ChatRequest request) {
        String userText = safeText(request.getMessage());
        ChatModel chatModel = requireChatModel();
        Long userId = AiUserContext.peekUserId();
        log.info("AI chat (non-stream) session={}, text-len={}, systemPrompt={}",
                request.getSessionId(), userText.length(),
                systemPrompt.isBlank() ? "<none>" : "<configured>");

        try {
            // 先把 user 消息落库（长期记忆 MySQL）
            persistMessage(userId, request.getSessionId(), "user", userText, null);
            // 短期记忆 Redis
            if (userId != null && request.getSessionId() != null) {
                shortTermMemory.appendUserMessage(userId, request.getSessionId(), userText);
            }

            String aiText = chatWithTools(chatModel, userId, request.getSessionId(), userText);
            // 落 AI 消息（长期记忆 MySQL）
            persistMessage(userId, request.getSessionId(), "ai", aiText, null);
            // 短期记忆 Redis
            if (userId != null && request.getSessionId() != null) {
                shortTermMemory.appendAiMessage(userId, request.getSessionId(), aiText);
            }

            // 自动生成标题（首次有消息且标题是默认"新会话"）
            autoGenerateTitle(userId, request.getSessionId(), userText);

            return ChatResponse.builder()
                    .sessionId(request.getSessionId())
                    .message(ChatResponse.ChatMessage.builder()
                            .id(UUID.randomUUID().toString())
                            .role("ai")
                            .content(aiText)
                            .createdAt(System.currentTimeMillis())
                            .build())
                    .build();
        } finally {
            AiUserContext.clear();
        }
    }

    // ==================== 流式 ====================

    public void chatStream(ChatRequest request,
                           Consumer<String> onChunk,
                           BiConsumer<String, List<Map<String, Object>>> onComplete,
                           Consumer<Throwable> onError) {
        StreamingChatModel streamingChatModel;
        try {
            streamingChatModel = requireStreamingChatModel();
        } catch (AiDisabledException e) {
            onError.accept(e);
            return;
        }
        String userText = safeText(request.getMessage());
        Long userId = AiUserContext.peekUserId();
        log.info("AI chat (stream) session={}, text-len={}",
                request.getSessionId(), userText.length());

        // 先把 user 消息落库（长期记忆 MySQL）
        persistMessage(userId, request.getSessionId(), "user", userText, null);
        // 短期记忆 Redis
        if (userId != null && request.getSessionId() != null) {
            shortTermMemory.appendUserMessage(userId, request.getSessionId(), userText);
        }

        List<ToolSpecification> toolSpecs = toolSpecifications;
        Map<String, Object> toolBeanMap = this.toolBeanMap;
        ObjectMapper mapper = this.objectMapper;
        SimpleToolExecutor executor = (toolBeanMap != null && mapper != null)
                ? new SimpleToolExecutor(toolBeanMap, mapper) : null;

        // 构造 messages（System + 短期记忆 + 本轮 User）
        List<ChatMessage> messages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(SystemMessage.from(systemPrompt));
        }
        // 注入短期记忆（Redis；失效时按需 MySQL 重建）
        if (userId != null && request.getSessionId() != null) {
            messages.addAll(shortTermMemory.loadMemory(userId, request.getSessionId()));
        }
        messages.add(UserMessage.from(userText));

        ChatRequestParameters params = ChatRequestParameters.builder()
                .toolSpecifications(toolSpecs == null ? List.of() : toolSpecs)
                .build();
        log.info("[AI-STREAM] 工具数量={} 名称={}",
                (toolSpecs == null ? 0 : toolSpecs.size()),
                (toolSpecs == null ? List.of() : toolSpecs.stream().map(ToolSpecification::name).toList()));

        streamWithTools(streamingChatModel, messages, params, executor,
                userId, request.getSessionId(), userText,
                onChunk, onComplete, onError, 0, null);
    }

    /**
     * 流式 Tool Loop：每一轮 streaming 返回 partialResponse / toolExecutionRequests。
     * 因为 DashScope 的 StreamingChatModel 不暴露中间 toolExecutionRequests，
     * 流式实现策略简化为：
     * - 第 1 轮 streaming 给用户输出 token
     * - 若需要工具调用（流式无法拦截），先收集完文本后切到非流式完成 tool loop
     * - 最后再把"工具结果 + 最终回复"作为新一轮 streaming 输出给用户
     */
    private void streamWithTools(StreamingChatModel model,
                                  List<ChatMessage> messages,
                                  ChatRequestParameters params,
                                  SimpleToolExecutor executor,
                                  Long userId,
                                  String sessionId,
                                  String userText,
                                  Consumer<String> onChunk,
                                  BiConsumer<String, List<Map<String, Object>>> onComplete,
                                  Consumer<Throwable> onError,
                                  int iteration,
                                  List<Map<String, Object>> imagesFromParent) {
        if (iteration >= MAX_TOOL_ITERATIONS) {
            log.warn("[AI-STREAM] tool loop 超过最大迭代次数，强制结束");
            onComplete.accept("", imagesFromParent == null ? List.of() : imagesFromParent);
            return;
        }

        StringBuilder fullText = new StringBuilder();
        boolean[] hasToolRequests = {false};
        // 累积本轮所有工具调用中产生的图片 URL（type==="image" 的结果），
        // 在最终 AI 答复发出时一次性随 done 帧推给前端。
        List<Map<String, Object>> accumulatedImages = imagesFromParent == null
                ? new ArrayList<>() : imagesFromParent;
        dev.langchain4j.model.chat.request.ChatRequest firstReq =
                dev.langchain4j.model.chat.request.ChatRequest.builder()
                        .messages(messages)
                        .parameters(params)
                        .build();

        StreamingChatResponseHandler handler = new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partial) {
                fullText.append(partial);
                try { onChunk.accept(partial); } catch (Exception e) { log.warn("onChunk err", e); }
            }

            @Override
            public void onCompleteResponse(dev.langchain4j.model.chat.response.ChatResponse response) {
                AiMessage aiMsg = response.aiMessage();
                if (aiMsg.hasToolExecutionRequests()) {
                    log.info("[AI-STREAM] LLM 触发工具调用 iter={} count={}", iteration, aiMsg.toolExecutionRequests().size());
                    for (ToolExecutionRequest ter : aiMsg.toolExecutionRequests()) {
                        log.info("[AI-STREAM]   -> {} args={}", ter.name(), ter.arguments());
                    }
                    hasToolRequests[0] = true;
                    // 把 AiMessage 拼回去，继续下一轮工具调用
                    messages.add(aiMsg);
                    // 执行所有工具
                    if (executor == null) {
                        onError.accept(new IllegalStateException("Tool calls requested but no executor"));
                        return;
                    }
                    for (ToolExecutionRequest ter : aiMsg.toolExecutionRequests()) {
                        // 工具回调在 DashScope 的 OkHttp SSE 线程里执行，原 ThreadLocal 已不可用；
                        // 这里用 lambda 闭包持有的 userId 重新绑定上下文
                        AiUserContext.set(userId, userId);
                        Object result;
                        try {
                            result = executor.execute(ter);
                        } finally {
                            AiUserContext.clear();
                        }
                        String resultJson = serialize(result);
                        log.info("[AI-STREAM]   <- {} rawType={} result={}", ter.name(),
                                result == null ? "null" : result.getClass().getSimpleName(), resultJson);
                        messages.add(ToolExecutionResultMessage.from(ter, resultJson));
                        // 抽取工具结果中的图片，供前端气泡渲染缩略图
                        collectImages(result, accumulatedImages);
                    }
                    streamWithTools(model, messages, params, executor,
                            userId, sessionId, userText,
                            onChunk, onComplete, onError, iteration + 1,
                            accumulatedImages);
                } else {
                    String finalText = fullText.toString();
                    // 流式结束后落 AI 消息 + 自动标题
                    persistMessage(userId, sessionId, "ai", finalText, null);
                    // 短期记忆 Redis
                    if (userId != null && sessionId != null) {
                        shortTermMemory.appendAiMessage(userId, sessionId, finalText);
                    }
                    autoGenerateTitle(userId, sessionId, userText);
                    try { onComplete.accept(finalText, accumulatedImages); }
                    catch (Exception e) { log.warn("onComplete err", e); }
                }
            }

            @Override
            public void onError(Throwable error) {
                try { onError.accept(error); } catch (Exception e) { log.warn("onError err", e); }
            }
        };

        try {
            model.chat(firstReq, handler);
        } catch (Exception e) {
            onError.accept(e);
        }
    }

    // ==================== 非流式 Tool Loop ====================

    private String chatWithTools(ChatModel chatModel, Long userId, String sessionId, String userText) {
        List<ToolSpecification> toolSpecs = toolSpecifications;
        Map<String, Object> toolBeanMap = this.toolBeanMap;
        ObjectMapper mapper = this.objectMapper;
        SimpleToolExecutor executor = (toolBeanMap != null && mapper != null)
                ? new SimpleToolExecutor(toolBeanMap, mapper) : null;

        // 构造 messages（System + 短期记忆 + 本轮 User）
        List<ChatMessage> messages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(SystemMessage.from(systemPrompt));
        }
        if (userId != null && sessionId != null) {
            messages.addAll(shortTermMemory.loadMemory(userId, sessionId));
        }
        messages.add(UserMessage.from(userText));

        ChatRequestParameters params = ChatRequestParameters.builder()
                .toolSpecifications(toolSpecs == null ? List.of() : toolSpecs)
                .build();

        for (int i = 0; i < MAX_TOOL_ITERATIONS; i++) {
            dev.langchain4j.model.chat.request.ChatRequest req =
                    dev.langchain4j.model.chat.request.ChatRequest.builder()
                            .messages(messages)
                            .parameters(params)
                            .build();
            dev.langchain4j.model.chat.response.ChatResponse resp = chatModel.chat(req);
            AiMessage aiMsg = resp.aiMessage();
            if (!aiMsg.hasToolExecutionRequests()) {
                return aiMsg.text() == null ? "" : aiMsg.text();
            }
            // 有工具调用：拼回 messages，执行工具，结果回灌
            messages.add(aiMsg);
            if (executor == null) {
                log.warn("[AI] 有工具调用但 executor 为空，跳过");
                return "抱歉，工具调用失败";
            }
            Long ctxUserId = AiUserContext.peekUserId();
            for (ToolExecutionRequest ter : aiMsg.toolExecutionRequests()) {
                // 防御性：如果 ThreadLocal 已被清空，回填（从 chat() 入口 peek 出来的 userId）
                AiUserContext.set(ctxUserId != null ? ctxUserId : 0L, ctxUserId != null ? ctxUserId : 0L);
                Object result;
                try {
                    result = executor.execute(ter);
                } finally {
                    AiUserContext.clear();
                }
                String resultJson = serialize(result);
                log.info("[AI-CHAT]   <- {} rawType={} result={}", ter.name(),
                        result == null ? "null" : result.getClass().getSimpleName(), resultJson);
                messages.add(ToolExecutionResultMessage.from(ter, resultJson));
            }
        }
        log.warn("[AI] tool loop 超过最大迭代次数");
        return "抱歉，思考过久未给出答案，请换一种问法";
    }

    // ==================== 工具 ====================

    private ChatModel requireChatModel() {
        ChatModel m = chatModelProvider.getIfAvailable();
        if (m == null) throw new AiDisabledException("AI 服务未启用或未配置 API Key");
        return m;
    }

    private StreamingChatModel requireStreamingChatModel() {
        StreamingChatModel m = streamingChatModelProvider.getIfAvailable();
        if (m == null) throw new AiDisabledException("AI 服务未启用或未配置 API Key");
        return m;
    }

    private String safeText(String text) {
        if (text == null) return "";
        String trimmed = text.trim();
        if (trimmed.isEmpty()) throw new IllegalArgumentException("消息内容不能为空");
        if (trimmed.length() > 2000) return trimmed.substring(0, 2000);
        return trimmed;
    }

    private String serialize(Object obj) {
        if (obj == null) return "{}";
        if (obj instanceof String) return (String) obj;
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("[AI] serialize 失败", e);
            return obj.toString();
        }
    }

    /**
     * 从工具返回结果中抽取"图片"项（type==="image" 或含 imageUrl 字段），
     * 累积到 images 列表，供 AI 答复气泡中以缩略图形式展示。
     */
    @SuppressWarnings("unchecked")
    private void collectImages(Object result, List<Map<String, Object>> images) {
        if (result == null) return;
        if (result instanceof Map) {
            Object type = ((Map<?, ?>) result).get("type");
            Object url = ((Map<?, ?>) result).get("imageUrl");
            if ("image".equals(type) && url instanceof String) {
                Map<String, Object> copy = new LinkedHashMap<>((Map<String, Object>) result);
                images.add(copy);
            }
            return;
        }
        if (result instanceof List) {
            for (Object item : (List<?>) result) {
                collectImages(item, images);
            }
        }
    }

    // ==================== 会话持久化 ====================

    private void persistMessage(Long userId, String sessionId, String role, String content, String toolName) {
        if (userId == null || sessionId == null || sessionId.isBlank()) return;
        try {
            AiMessageVO vo = new AiMessageVO();
            vo.setRole(role);
            vo.setContent(content == null ? "" : content);
            vo.setToolName(toolName);
            vo.setCreatedAt(java.time.LocalDateTime.now());
            aiSessionService.appendMessage(userId.intValue(), sessionId, vo);
        } catch (Exception e) {
            log.warn("[AI] 消息持久化失败 session={}, role={}", sessionId, role, e);
        }
    }

    /**
     * 自动生成会话标题：仅当 title 仍为 "新会话" 时替换为用户首条问题（截断到 24 字）。
     * <p>
     * 留作未来 LLM 自动总结的扩展点；目前采用简单截断方案，避免额外 LLM 开销。
     */
    private void autoGenerateTitle(Long userId, String sessionId, String firstUserText) {
        if (userId == null || firstUserText == null) return;
        try {
            var summary = aiSessionService.listSessions(userId.intValue());
            var data = summary == null ? null : summary.getData();
            if (data == null) return;
            var hit = data.stream().filter(v -> sessionId.equals(v.getSessionId())).findFirst();
            if (hit.isEmpty()) return;
            String title = hit.get().getTitle();
            if (title != null && ! title.equals("新会话")) return; // 已被用户重命名或已生成过

            String newTitle = firstUserText.trim();
            if (newTitle.length() > 24) newTitle = newTitle.substring(0, 24) + "…";
            if (newTitle.isBlank()) newTitle = "新会话";
            var renameReq = new com.example.lovemap.ai.dto.AiRenameRequest();
            renameReq.setTitle(newTitle);
            aiSessionService.renameSession(userId.intValue(), sessionId, renameReq);
        } catch (Exception e) {
            log.warn("[AI] 自动生成标题失败", e);
        }
    }
}