package com.example.lovemap.ai.config;

import com.example.lovemap.ai.tool.AlbumTool;
import com.example.lovemap.ai.tool.AnniversaryTool;
import com.example.lovemap.ai.tool.ExportTool;
import com.example.lovemap.ai.tool.GiftRecommendTool;
import com.example.lovemap.ai.tool.MemoryReportTool;
import com.example.lovemap.ai.tool.MoodTool;
import com.example.lovemap.ai.tool.NotificationSettingsTool;
import com.example.lovemap.ai.tool.PartnerTool;
import com.example.lovemap.ai.tool.PhotoInsightTool;
import com.example.lovemap.ai.tool.PhotoTool;
import com.example.lovemap.ai.tool.PhotoUploadTool;
import com.example.lovemap.ai.tool.ReminderTool;
import com.example.lovemap.ai.tool.TimeRangeTool;
import com.example.lovemap.ai.tool.UserProfileTool;
import com.example.lovemap.ai.tool.UserStatsTool;
import com.example.lovemap.common.ServiceHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.community.model.dashscope.QwenChatModel;
import dev.langchain4j.community.model.dashscope.QwenStreamingChatModel;
import dev.langchain4j.community.model.dashscope.WanxImageModel;
import dev.langchain4j.community.model.dashscope.WanxImageRefMode;
import dev.langchain4j.community.model.dashscope.WanxImageSize;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.image.ImageModel;
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
        logDashScopeRuntimeInfo("chat", modelName);
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
        logDashScopeRuntimeInfo("streaming", modelName);
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
     * 打印 DashScope SDK 版本与默认端点，便于排查 url error 类问题。
     * <p>
     * 不同版本 / 不同端点的组合会导致 DashScope 服务端返回
     * "InvalidParameter: url error, please check url"。常见组合：
     * <ul>
     *   <li>qwen3.x + 旧 SDK：默认走纯文本端点，多模态场景被拒</li>
     *   <li>qwen-plus + thinking=true：默认 incremental_output 路径不匹配</li>
     * </ul>
     */
    private void logDashScopeRuntimeInfo(String channel, String modelName) {
        String sdkVersion = readPackageImplementationVersion("com.alibaba.dashscope");
        log.info("[AI-DIAG] DashScope channel={}, model={}, sdk.version={}",
                channel, modelName, sdkVersion == null ? "<unknown>" : sdkVersion);
    }

    private String readPackageImplementationVersion(String pkg) {
        try {
            Package p = Package.getPackage(pkg);
            return p == null ? null : p.getImplementationVersion();
        } catch (Exception ignored) {
            return null;
        }
    }

    // ======================== 化妆建议专用多模态 ChatModel ========================
    // 配置位于 ai.dashscope.makeover.*，独立于通用模型，避免互相影响

    @Value("${ai.dashscope.makeover.enabled:true}")
    private boolean makeoverEnabled;

    @Value("${ai.dashscope.makeover.api-key:}")
    private String makeoverApiKey;

    @Value("${ai.dashscope.makeover.model-name:qwen3.8-flash}")
    private String makeoverModelName;

    @Value("${ai.dashscope.makeover.temperature:0.5}")
    private Float makeoverTemperature;

    @Value("${ai.dashscope.makeover.max-tokens:2000}")
    private Integer makeoverMaxTokens;

    /**
     * 化妆建议专用多模态 ChatModel（Qwen-VL）
     * <p>
     * 与通用 Bean 解耦：模型名 / 温度 / Key 都可独立覆盖，
     * 即便通用模型挂了也不影响妆造分析。
     */
    @Bean(name = "dashscopeMakeoverChatModel")
    public ChatModel dashscopeMakeoverChatModel() {
        if (!makeoverEnabled) {
            log.warn("[AI] ai.dashscope.makeover.enabled=false，跳过 Makeover ChatModel 装配");
            return null;
        }
        String key = (makeoverApiKey == null || makeoverApiKey.isBlank()) ? apiKey : makeoverApiKey;
        if (key == null || key.isBlank()) {
            log.error("[AI] DASHSCOPE_MAKEOVER_API_KEY / DASHSCOPE_API_KEY 均未配置，Makeover ChatModel 将为 null");
            return null;
        }
        log.info("[AI] 初始化 Makeover ChatModel: model={}, temperature={}, maxTokens={}",
                makeoverModelName, makeoverTemperature, makeoverMaxTokens);
        return QwenChatModel.builder()
                .apiKey(key)
                .modelName(makeoverModelName)
                .temperature(makeoverTemperature)
                .maxTokens(makeoverMaxTokens)
                .build();
    }

    // ======================== 改造图生成 ImageModel ========================
    // 配置位于 ai.dashscope.image-edit.*，独立 Bean，依赖 WanxImageModel.edit()

    @Value("${ai.dashscope.image-edit.enabled:true}")
    private boolean imageEditEnabled;

    @Value("${ai.dashscope.image-edit.api-key:}")
    private String imageEditApiKey;

    @Value("${ai.dashscope.image-edit.model-name:wanx2.1-imageedit}")
    private String imageEditModelName;

    @Value("${ai.dashscope.image-edit.ref-mode:REPAINT}")
    private String imageEditRefMode;

    @Value("${ai.dashscope.image-edit.size:1024*1024}")
    private String imageEditSize;

    /**
     * 改造图 ImageModel（基于 WanxImageModel）
     * <p>
     * Stage2 文本驱动编辑的工作流：原图 + prompt → 新图。
     * WanxImageModel.edit(Image, String) 内部封装了原 ImageSynthesis 异步调用。
     * <p>
     * 注意：langchain4j 1.18.0-beta28 的 WanxImageModel.edit() 不会填充 function / base_image_url
     * 必填字段。当前妆容改造链路 {@link com.example.lovemap.makeover.ai.impl.DashScopeWanxImageProvider}
     * 已绕过 WanxImageModel，直接调原生 SDK。本 Bean 保留用于其他可能的非 Wanx 用法。
     */
    @Bean(name = "dashscopeImageEditModel")
    public ImageModel dashscopeImageEditModel() {
        if (!imageEditEnabled) {
            log.warn("[AI] ai.dashscope.image-edit.enabled=false，跳过 Makeover ImageModel 装配");
            return null;
        }
        String key = (imageEditApiKey == null || imageEditApiKey.isBlank()) ? apiKey : imageEditApiKey;
        if (key == null || key.isBlank()) {
            log.error("[AI] DASHSCOPE_MAKEOVER_API_KEY / DASHSCOPE_API_KEY 均未配置，ImageEditModel 将为 null");
            return null;
        }
        WanxImageRefMode refMode = "REFONLY".equalsIgnoreCase(imageEditRefMode)
                ? WanxImageRefMode.REFONLY
                : WanxImageRefMode.REPAINT;
        WanxImageSize size = parseWanxSize(imageEditSize);
        log.info("[AI] 初始化 ImageEditModel: model={}, refMode={}, size={}",
                imageEditModelName, refMode, size);
        return WanxImageModel.builder()
                .apiKey(key)
                .modelName(imageEditModelName)
                .refMode(refMode)
                .size(size)
                .promptExtend(true)
                .watermark(false)
                .build();
    }

    private WanxImageSize parseWanxSize(String sizeStr) {
        if (sizeStr == null) return WanxImageSize.SIZE_1024_1024;
        return switch (sizeStr) {
            case "720*1280" -> WanxImageSize.SIZE_720_1280;
            case "1280*720" -> WanxImageSize.SIZE_1280_720;
            case "1024*1024" -> WanxImageSize.SIZE_1024_1024;
            default -> WanxImageSize.SIZE_1024_1024;
        };
    }

    /**
     * 工具列表：把 4 个 Tool 类的所有 @Tool 方法转成 ToolSpecification 列表，
     * 供 AiChatService 注入到 ChatRequest.parameters.toolSpecifications
     */
    @Bean
    public List<ToolSpecification> aiToolSpecifications(PhotoTool photoTool,
                                                         AnniversaryTool anniversaryTool,
                                                         AlbumTool albumTool,
                                                         PhotoUploadTool photoUploadTool,
                                                         UserStatsTool userStatsTool,
                                                         UserProfileTool userProfileTool,
                                                         ExportTool exportTool,
                                                         MemoryReportTool memoryReportTool,
                                                         TimeRangeTool timeRangeTool,
                                                         PartnerTool partnerTool,
                                                         MoodTool moodTool,
                                                         ReminderTool reminderTool,
                                                         PhotoInsightTool photoInsightTool,
                                                         NotificationSettingsTool notificationSettingsTool,
                                                         GiftRecommendTool giftRecommendTool) {
        List<ToolSpecification> specs = new java.util.ArrayList<>();
        for (Object toolObj : List.of(photoTool, anniversaryTool, albumTool, photoUploadTool,
                userStatsTool, userProfileTool, exportTool, memoryReportTool, timeRangeTool,
                partnerTool, moodTool, reminderTool, photoInsightTool, notificationSettingsTool,
                giftRecommendTool)) {
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
                                              PhotoUploadTool photoUploadTool,
                                              UserStatsTool userStatsTool,
                                              UserProfileTool userProfileTool,
                                              ExportTool exportTool,
                                              MemoryReportTool memoryReportTool,
                                              TimeRangeTool timeRangeTool,
                                              PartnerTool partnerTool,
                                              MoodTool moodTool,
                                              ReminderTool reminderTool,
                                              PhotoInsightTool photoInsightTool,
                                              NotificationSettingsTool notificationSettingsTool,
                                              GiftRecommendTool giftRecommendTool) {
        Map<String, Object> map = new HashMap<>();
        map.put("searchPhotos", photoTool);
        map.put("describePhoto", photoTool);
        map.put("prepareDeletePhoto", photoTool);
        map.put("confirmDeletePhoto", photoTool);
        map.put("queryAnniversaries", anniversaryTool);
        map.put("searchAnniversaryByName", anniversaryTool);
        map.put("getCountdownByName", anniversaryTool);
        map.put("collectAnniversaryField", anniversaryTool);
        map.put("checkAnniversaryDraft", anniversaryTool);
        map.put("prepareCreateAnniversary", anniversaryTool);
        map.put("confirmCreateAnniversary", anniversaryTool);
        map.put("prepareUpdateAnniversary", anniversaryTool);
        map.put("confirmUpdateAnniversary", anniversaryTool);
        map.put("prepareDeleteAnniversary", anniversaryTool);
        map.put("confirmDeleteAnniversary", anniversaryTool);
        map.put("listAlbums", albumTool);
        map.put("searchAlbumByName", albumTool);
        map.put("prepareAddPhotosToAlbum", albumTool);
        map.put("prepareRemovePhotoFromAlbum", albumTool);
        map.put("prepareUpdateAlbum", albumTool);
        map.put("prepareDeleteAlbum", albumTool);
        map.put("prepareCreateAlbum", albumTool);
        map.put("confirmAlbumOp", albumTool);
        map.put("collectUploadField", photoUploadTool);
        map.put("checkUploadDraft", photoUploadTool);
        map.put("resolveAlbum", photoUploadTool);
        map.put("prepareUpload", photoUploadTool);
        map.put("confirmUpload", photoUploadTool);
        map.put("getUserStats", userStatsTool);
        map.put("getMyProfile", userProfileTool);
        map.put("prepareUpdateNickname", userProfileTool);
        map.put("confirmUpdateNickname", userProfileTool);
        map.put("prepareUpdatePhone", userProfileTool);
        map.put("confirmUpdatePhone", userProfileTool);
        map.put("prepareUpdateEmail", userProfileTool);
        map.put("confirmUpdateEmail", userProfileTool);
        map.put("listExportHistory", exportTool);
        map.put("getExportStatus", exportTool);
        map.put("prepareCreateExport", exportTool);
        map.put("confirmCreateExport", exportTool);
        map.put("generateMonthlyReport", memoryReportTool);
        map.put("generateYearlyReport", memoryReportTool);
        map.put("getCoupleMilestones", memoryReportTool);
        map.put("today", timeRangeTool);
        map.put("recentDays", timeRangeTool);
        map.put("monthRange", timeRangeTool);
        map.put("getCurrentTime", timeRangeTool);
        map.put("getPartnerInfo", partnerTool);
        map.put("getTodayMood", moodTool);
        map.put("recordMood", moodTool);
        map.put("listReminders", reminderTool);
        map.put("prepareSetReminder", reminderTool);
        map.put("confirmSetReminder", reminderTool);
        map.put("getPhotoTimeline", photoInsightTool);
        map.put("searchAnniversariesByMonth", photoInsightTool);
        map.put("getMostVisitedCity", photoInsightTool);
        map.put("getRecentPhotos", photoInsightTool);
        map.put("getAnniversaryStats", photoInsightTool);
        map.put("generateWeeklyReport", photoInsightTool);
        map.put("getNotificationSettings", notificationSettingsTool);
        map.put("prepareUpdateNotificationSettings", notificationSettingsTool);
        map.put("confirmUpdateNotificationSettings", notificationSettingsTool);
        map.put("generateAnniversaryCopy", anniversaryTool);
        map.put("generateUpcomingAnniversaryCopies", anniversaryTool);
        map.put("submitAnniversaryCopyFeedback", anniversaryTool);
        map.put("recommendGiftsByPreference", giftRecommendTool);
        map.put("recommendGiftsFromWishlist", giftRecommendTool);
        map.put("recommendGiftsForAnniversary", giftRecommendTool);
        return map;
    }
}