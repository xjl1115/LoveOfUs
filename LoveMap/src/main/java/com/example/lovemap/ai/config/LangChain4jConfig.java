package com.example.lovemap.ai.config;

import dev.langchain4j.community.model.dashscope.QwenChatModel;
import dev.langchain4j.community.model.dashscope.QwenStreamingChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * LangChain4j + DashScope（通义千问）装配
 * <p>
 * 关键策略：
 * 1. 不引入 langchain4j-spring-boot-starter，避免自动装配冲突
 * 2. 通过 @ConditionalOnProperty(ai.enabled=true) 控制是否生成 Bean
 * 3. API Key 缺失：启动期记 ERROR，但应用继续启动；Controller 会降级返回 503
 * <p>
 * 注：DashScope 的 QwenChatModelBuilder 不提供 defaultSystemMessage(...) 方法，
 * 所以 system prompt 改为在调用层 AiChatService 中以 SystemMessage 形式拼接进 messages 列表，
 * 配置项仍由本类读取并暴露成 systemPrompt Bean 注入。
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
     * 系统提示词（可选），从 yml 注入；为空则不传 SystemMessage，使用 Qwen 默认行为
     */
    @Value("${ai.dashscope.system-prompt:}")
    private String systemPrompt;

    /**
     * 非流式 ChatModel
     * <p>
     * 未配置 API Key 时返回 null Bean，由 AiChatController 通过 ObjectProvider 安全获取
     */
    @Bean
    public ChatModel dashscopeChatModel() {
        if (apiKey == null || apiKey.isBlank()) {
            log.error("[AI] DASHSCOPE_API_KEY 未配置，AI ChatModel Bean 将为 null，相关接口将返回 503");
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
}