package com.example.lovemap.ai.config;

import com.example.lovemap.ai.tool.AlbumTool;
import com.example.lovemap.ai.tool.AnniversaryTool;
import com.example.lovemap.ai.tool.PhotoTool;
import com.example.lovemap.ai.tool.UserStatsTool;
import com.example.lovemap.common.ServiceHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.community.model.dashscope.QwenChatModel;
import dev.langchain4j.community.model.dashscope.QwenStreamingChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * LangChain4j + DashScope（通义千问）装配
 * <p>
 * 关键策略：
 * 1. 不引入 langchain4j-spring-boot-starter，避免自动装配冲突
 * 2. 通过 @ConditionalOnProperty(ai.enabled=true) 控制是否生成 Bean
 * 3. API Key 缺失：启动期记 ERROR，但应用继续启动；Controller 会降级返回 503
 * <p>
 * 注：LangChain4j 1.18 已废弃 AiServices.builder().tools(...) 的写法，
 * Tool Calling 改为在 AiChatService 中以 Low-level 方式手动循环：
 * - ChatModel.chat(ChatRequest) 携带 toolSpecifications
 * - 解析 AiMessage.toolExecutionRequests()，用 DefaultToolExecutor 执行
 * - 把 ToolExecutionResultMessage 追加回 messages 再发起下一轮
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "ai", name = "enabled", havingValue = "true", matchIfMissing = true)
public class LangChain4jConfig {

    @Value("${ai.dashscope.api-key:}")
    private String apiKey;

    @Value("${ai.dashscope.model-name:qwen-plus}")
    private String modelName;

    @Value("${ai.dashscope.temperature:0.7}")
    private Float temperature;

    @Value("${ai.dashscope.max-tokens:1500}")
    private Integer maxTokens;

    @Value("${ai.dashscope.timeout-seconds:60}")
    private Integer timeoutSeconds;

    /**
     * 系统提示词（可选），从 yml 注入
     */
    @Value("${ai.dashscope.system-prompt:}")
    private String systemPrompt;

    /**
     * 非流式 ChatModel
     */
    @Bean
    public ChatModel dashscopeChatModel() {
        if (apiKey == null || apiKey.isBlank()) {
            log.error("[AI] DASHSCOPE_API_KEY 未配置，AI ChatModel Bean 将为 null");
            return null;
        }
        log.info("初始化 DashScope ChatModel: model={}, systemPrompt={}", modelName,
                systemPrompt.isBlank() ? "<none>" : "<configured>");
        return QwenChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .build();
    }

    /**
     * 流式 ChatModel
     */
    @Bean
    public StreamingChatModel dashscopeStreamingChatModel() {
        if (apiKey == null || apiKey.isBlank()) {
            log.error("[AI] DASHSCOPE_API_KEY 未配置，AI StreamingChatModel Bean 将为 null");
            return null;
        }
        log.info("初始化 DashScope StreamingChatModel: model={}, systemPrompt={}", modelName,
                systemPrompt.isBlank() ? "<none>" : "<configured>");
        return QwenStreamingChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .build();
    }

    /**
     * 工具列表：把 4 个 Tool 类的所有 @Tool 方法转成 ToolSpecification 列表，
     * 供 AiChatService 注入到 ChatRequest.parameters.toolSpecifications
     */
    @Bean
    public List<ToolSpecification> aiToolSpecifications(PhotoTool photoTool,
                                                         AnniversaryTool anniversaryTool,
                                                         AlbumTool albumTool,
                                                         UserStatsTool userStatsTool) {
        List<ToolSpecification> specs = new java.util.ArrayList<>();
        for (Object toolObj : List.of(photoTool, anniversaryTool, albumTool, userStatsTool)) {
            specs.addAll(ToolSpecifications.toolSpecificationsFrom(toolObj));
        }
        log.info("[AI] 工具列表：{}", specs.stream().map(ToolSpecification::name).toList());
        return specs;
    }

    /**
     * 工具对象映射：toolName -> toolBean，用于 ToolExecutor 找到正确的实例。
     * 注意 DefaultToolExecutor 按方法签名调用即可，无需此 Bean。
     */
    @Bean
    public Map<String, Object> aiToolBeanMap(PhotoTool photoTool,
                                              AnniversaryTool anniversaryTool,
                                              AlbumTool albumTool,
                                              UserStatsTool userStatsTool) {
        Map<String, Object> map = new HashMap<>();
        map.put("searchPhotos", photoTool);
        map.put("describePhoto", photoTool);
        map.put("queryAnniversaries", anniversaryTool);
        map.put("searchAnniversaryByName", anniversaryTool);
        map.put("getCountdownByName", anniversaryTool);
        map.put("prepareCreateAnniversary", anniversaryTool);
        map.put("confirmCreateAnniversary", anniversaryTool);
        map.put("listAlbums", albumTool);
        map.put("searchAlbumByName", albumTool);
        map.put("getUserStats", userStatsTool);
        return map;
    }
}