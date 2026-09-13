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
import java.util.Collections;
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
    /** 工具循环最大迭代次数。注：完成"创建纪念日"等分阶段引导流程至少需要 8 次迭代（5 个 collect + check + prepare + confirm），
     *  原值 5 会在收齐字段后被强制中断，导致流程无法闭环。 */
    private static final int MAX_TOOL_ITERATIONS = 12;

    public AiChatService(@Qualifier("dashscopeChatModel") ObjectProvider<ChatModel> chatModelProvider,
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
        log.info("AI chat (stream) session={}, text-len={}, modelBean={}",
                request.getSessionId(), userText.length(),
                streamingChatModel == null ? "<null>" : streamingChatModel.getClass().getName());

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
                onChunk, onComplete, onError, 0, null,
                new ArrayList<>());
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
                                  List<Map<String, Object>> imagesFromParent,
                                  List<String> calledToolNames) {
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
                // 防御：DashScope 在某些场景（url error、限流、超时取消）会回调
                // onCompleteResponse 但 response==null。旧代码直接 response.aiMessage()
                // 会 NPE，把真正的异常吞掉，让排查无法继续。这里先记录并优雅退出。
                if (response == null) {
                    log.warn("[AI-STREAM] onCompleteResponse 收到 null response（iter={}）— DashScope "
                            + "服务端可能已断开 / 上游已抛 ApiException，结束当前流。iteration={}, "
                            + "fullText.length={}, hasToolRequests={}",
                            iteration, fullText.length(), hasToolRequests[0]);
                    try {
                        onComplete.accept(fullText.toString(),
                                new ArrayList<>(accumulatedImages));
                    } catch (Exception notifyErr) {
                        log.warn("[AI-STREAM] onComplete.accept 通知失败（response==null）: {}", notifyErr.toString());
                    }
                    return;
                }
                AiMessage aiMsg = response.aiMessage();
                if (aiMsg == null) {
                    log.warn("[AI-STREAM] response.aiMessage() 为 null（iter={}）", iteration);
                    try {
                        onComplete.accept(fullText.toString(),
                                new ArrayList<>(accumulatedImages));
                    } catch (Exception notifyErr) {
                        log.warn("[AI-STREAM] onComplete.accept 通知失败（aiMsg==null）: {}", notifyErr.toString());
                    }
                    return;
                }
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
                        calledToolNames.add(ter.name());
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
                            accumulatedImages, calledToolNames);
                } else {
                    String finalText = fullText.toString();
                    // 流式结束后落 AI 消息 + 自动标题
                    persistMessage(userId, sessionId, "ai", finalText, null);
                    // 短期记忆 Redis
                    if (userId != null && sessionId != null) {
                        shortTermMemory.appendAiMessage(userId, sessionId, finalText);
                    }
                    autoGenerateTitle(userId, sessionId, userText);
                    // 幻觉检测：成功词 + 未调 confirm* → WARN 日志
                    detectFabricatedSuccess(finalText, calledToolNames, userId, sessionId, "stream");
                    try { onComplete.accept(finalText, accumulatedImages); }
                    catch (Exception e) { log.warn("onComplete err", e); }
                }
            }

            @Override
            public void onError(Throwable error) {
                // 把 DashScope ApiException 的 requestId / code / statusCode 完整透出，
                // 替前端保留诊断上下文（旧版本只 dump toString，关键字段全丢）。
                logDashScopeError("[AI-STREAM] onError (iter={})", error, iteration);
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

        // 累计本轮所有 tool 调用名（含 prepare / confirm / 普通查询），
        // 用于在 AI 返回最终文本前做"声称成功但未真正写入"的幻觉检测。
        List<String> calledToolNames = new ArrayList<>();

        for (int i = 0; i < MAX_TOOL_ITERATIONS; i++) {
            dev.langchain4j.model.chat.request.ChatRequest req =
                    dev.langchain4j.model.chat.request.ChatRequest.builder()
                            .messages(messages)
                            .parameters(params)
                            .build();
            dev.langchain4j.model.chat.response.ChatResponse resp = chatModel.chat(req);
            AiMessage aiMsg = resp.aiMessage();
            if (!aiMsg.hasToolExecutionRequests()) {
                String finalText = aiMsg.text() == null ? "" : aiMsg.text();
                // 幻觉检测：成功词 + 未调 confirm* → WARN 日志
                detectFabricatedSuccess(finalText, calledToolNames, userId, sessionId, "chat");
                return finalText;
            }
            // 有工具调用：拼回 messages，执行工具，结果回灌
            messages.add(aiMsg);
            if (executor == null) {
                log.warn("[AI] 有工具调用但 executor 为空，跳过");
                return "抱歉，工具调用失败";
            }
            Long ctxUserId = AiUserContext.peekUserId();
            for (ToolExecutionRequest ter : aiMsg.toolExecutionRequests()) {
                calledToolNames.add(ter.name());
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

    // ==================== 幻觉检测埋点 ====================

    /**
     * 触发"声称成功但未真正写入"的幻觉检测。
     * <p>
     * 业务背景：很多 AI Tool（特别是二次确认类）的 prepare 阶段返回 status=CONFIRM_REQUIRED，
     * 但 LLM（qwen3.7-flash 等小模型）有时会跳过 confirm 工具，直接告诉用户"已修改/已关闭"，
     * 造成用户感知与实际状态不一致。本方法在 AI 文本即将返回给前端前做一次校验：
     * <ul>
     *   <li>如果文本中出现"成功类关键词"，但本轮对话里**没有调用过任何 confirm* 工具**，
     *       打 WARN 日志（事件名 AI-FABRICATED-SUCCESS），便于后续定位与统计。</li>
     *   <li>如果调用过至少一个 confirm* 工具，认为是合法成功，**不打**日志（避免噪音）。</li>
     *   <li>如果文本为空 / 不含成功关键词，**不打**日志。</li>
     * </ul>
     *
     * @param finalText   AI 最终返回给用户的文本（可能为空）
     * @param toolNames   本轮会话里所有被调用过的工具名（含 prepare / confirm / 普通查询）
     * @param userId      当前用户 ID（用于日志关联）
     * @param sessionId   当前会话 ID（用于日志关联）
     * @param source      调用来源标识，例如 "chat" / "stream"，便于区分链路
     */
    private void detectFabricatedSuccess(String finalText,
                                          List<String> toolNames,
                                          Long userId,
                                          String sessionId,
                                          String source) {
        if (finalText == null || finalText.isBlank()) return;
        if (toolNames == null) toolNames = Collections.emptyList();

        // 1. 文本里是否包含"成功类"关键词
        boolean hasSuccessKeyword = FABRICATED_SUCCESS_KEYWORDS.stream()
                .anyMatch(kw -> finalText.contains(kw));
        if (!hasSuccessKeyword) return;

        // 2. 是否调用过至少一个 confirm* 工具（白名单）
        boolean calledAnyConfirm = toolNames.stream().anyMatch(n -> n != null && n.startsWith("confirm"));
        if (calledAnyConfirm) return; // 合法成功，不告警

        // 3. 命中：日志告警
        log.warn(
                "[AI-FABRICATED-SUCCESS] source={} userId={} sessionId={} tools={} textPreview={}",
                source,
                userId,
                sessionId,
                toolNames,
                finalText.length() > 120 ? finalText.substring(0, 120) + "..." : finalText
        );
    }

    /**
     * "声称成功"的关键词集合。匹配规则：任一关键词作为子串出现在 AI 文本中即视为"声称成功"。
     * <p>
     * 调整原则：
     * <ul>
     *   <li>短语要够具体，避免"好的/收到/明白"这种通用词单独命中；</li>
     *   <li>覆盖口语化表达（"搞定/办好了/已经处理了"）和小模型常见幻觉；</li>
     *   <li>覆盖"隐含成功"的承诺句式（"以后就不会再…/立即生效/已更新到"）；</li>
     *   <li>避免否定句误报：所有关键词都表示**正面完成**，不含"还没/暂时没"等修饰。</li>
     * </ul>
     */
    private static final List<String> FABRICATED_SUCCESS_KEYWORDS = List.of(
            // ============ 1. 明确完成态（短词 + 长词） ============
            "已修改", "已保存", "已关闭", "已开启", "已删除", "已创建", "已加入",
            "已更新", "已绑定", "已解除", "已发送", "已上传", "已导出", "已建立",
            "已设置", "已调整", "已切换", "已替换", "已记录", "已登记", "已应用",
            "已添加", "已移除", "已取消", "已恢复", "已清空", "已重置",
            "已完成", "已处理", "已操作", "已确认", "已生效", "已同步",

            // ============ 2. 动作完成短语（口语化） ============
            "设置成功", "关闭成功", "开启成功", "删除成功", "创建成功",
            "修改成功", "保存成功", "绑定成功", "解除成功", "导出成功", "上传成功",
            "搞定", "搞定了", "办好了", "处理好了", "处理完毕",
            "完成了", "完成啦", "弄好了", "改好了", "删掉了", "关掉了", "打开了",
            "成功了", "成功啦", "弄完啦", "搞定啦",
            // "已帮您X/已帮你X" 系列：必须带动作动词，避免"好的，已帮您查询"误报
            "已帮您关", "已帮你关", "已帮您开", "已帮你开",
            "已帮您删", "已帮你删", "已帮您改",
            "已经帮您关", "已经帮你关", "已经帮您开", "已经帮你开",
            "已经帮您删", "已经帮你删", "已经帮您改", "已经帮你改",
            "已经帮您设置", "已经帮你设置", "已经帮您创建", "已经帮你创建",
            "已经帮您搞定", "已经帮你搞定", "已经帮您处理", "已经帮你处理",
            "已经为您关", "已经为你关", "已经为您开", "已经为你开",
            "已经为您删", "已经为你删", "已经为您改", "已经为你改",
            "帮您关", "帮你关", "帮您开", "帮你开", "帮您删", "帮你删",
            "帮您改", "帮你改", "帮您创建", "帮你创建", "帮您设置", "帮你设置",
            "帮您关掉", "帮你关掉", "帮您开启", "帮你开启", "帮您删除", "帮你删除",
            "帮您改掉", "帮你改掉", "帮您调整", "帮你调整", "帮您搞定", "帮你搞定",

            // ============ 3. 隐含成功的承诺句式 ============
            // 高置信度的"完成 + 状态变更"承诺，避开通用否定句（如"今天不会下雨"）
            "以后就不会再", "以后就不会再收到",
            "以后不会再收到", "以后都不会再",
            "以后不打扰您", "以后不打扰你",
            "不会再打扰您", "不会再打扰你",
            "立即生效", "马上生效", "立刻生效",
            "已经更新到", "已经写入", "已经同步到", "已经存储", "已经存入",
            "已经记到", "已经写入数据库", "已经更新数据库",

            // ============ 4. 通知 / 操作完成 的回执句 ============
            "已为您关闭", "已为你关闭", "已为您开启", "已为你开启",
            "已为您删除", "已为你删除", "已为您创建", "已为你创建",
            "已为您修改", "已为你修改", "已为您设置", "已为你设置",
            "已为您保存", "已为你保存", "已为您调整", "已为你调整",
            "已为您绑定", "已为你绑定", "已为您解除", "已为你解除",
            "已为您导出", "已为你导出", "已为您上传", "已为你上传",
            "已为您取消", "已为你取消",
            "已收到您的", "已经收到您的",
            "按您的要求"
    );

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

    // ==================== 诊断工具 ====================

    /**
     * 把 DashScope {@code ApiException} 的关键诊断字段（statusCode / code /
     * requestId / message）通过反射抽出来打到日志里。
     * <p>
     * 直接 {@code error.toString()} 会拼成 {@code ApiException: {"statusCode":400,"code":"..."}},
     * 但多个并发请求日志混在一起时定位不到具体哪一次失败——所以这里额外打印 requestId。
     * <p>
     * 该方法只对 {@code com.alibaba.dashscope.exception.ApiException} 起作用，
     * 其他异常类型 fallback 到普通 ERROR 日志。
     */
    private void logDashScopeError(String prefix, Throwable error, Object... args) {
        Object[] argArr = (args == null || args.length == 0) ? new Object[0] : args;
        String prefixResolved;
        try {
            prefixResolved = (argArr.length == 0) ? prefix : String.format(prefix, argArr);
        } catch (Exception fmtErr) {
            // prefix 里的占位符数量与 args 不匹配 → 原样输出
            prefixResolved = prefix + " (fmt-err: " + fmtErr.getMessage() + ")";
        }
        try {
            Class<?> apiExCls = Class.forName("com.alibaba.dashscope.exception.ApiException");
            if (apiExCls.isInstance(error)) {
                String statusCode = readStringAccessor(error, "getStatusCode");
                String code = readStringAccessor(error, "getCode");
                String reqId = readStringAccessor(error, "getRequestId");
                String msg = error.getMessage();
                log.error("{} statusCode={}, code={}, requestId={}, message={}, type={}",
                        prefixResolved, statusCode, code, reqId, msg,
                        error.getClass().getSimpleName(), error);
                return;
            }
        } catch (ClassNotFoundException ignored) {
            // DashScope SDK 不在 classpath（不应发生），降级
        }
        log.error("{} type={}, message={}", prefixResolved,
                error.getClass().getSimpleName(), error.getMessage(), error);
    }

    private String readStringAccessor(Object target, String methodName) {
        try {
            Object v = target.getClass().getMethod(methodName).invoke(target);
            return v == null ? "<null>" : v.toString();
        } catch (Exception e) {
            return "<unknown>";
        }
    }
}
