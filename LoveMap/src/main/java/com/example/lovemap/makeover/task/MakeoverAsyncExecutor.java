package com.example.lovemap.makeover.task;

import com.example.lovemap.ai.exception.AiErrorMessages;
import com.example.lovemap.makeover.ai.MakeoverImageProvider;
import com.example.lovemap.makeover.ai.MakeoverPromptBuilder;
import com.example.lovemap.makeover.ai.MakeoverResultParser;
import com.example.lovemap.makeover.constant.MakeoverConstant;
import com.example.lovemap.makeover.mapper.MakeoverRecordMapper;
import com.example.lovemap.makeover.mapper.MakeoverTaskMapper;
import com.example.lovemap.model.entity.MakeoverRecord;
import com.example.lovemap.model.entity.MakeoverTask;
import com.example.lovemap.utils.storage.FileStorage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

/**
 * AI 化妆建议异步执行器
 * <p>
 * 编排：Stage1 文本分析 → Stage2 改造图生成。
 * 任意阶段异常：捕获 → 状态置为失败 → SSE 推送 error。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MakeoverAsyncExecutor {

    private final MakeoverRecordMapper recordMapper;
    private final MakeoverTaskMapper taskMapper;
    private final MakeoverPromptBuilder promptBuilder;
    private final MakeoverResultParser resultParser;
    private final MakeoverImageProvider imageProvider;
    private final MakeoverTaskEventBus eventBus;
    private final FileStorage fileStorage;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;

    /**
     * 异步入口：复用现有 aiTaskExecutor 线程池（见 AsyncConfig）
     */
    @Async("aiTaskExecutor")
    public void run(Long recordId) {
        long start = System.currentTimeMillis();
        MakeoverRecord rec = recordMapper.selectById(recordId);
        if (rec == null) {
            log.warn("[Makeover-Async] recordId={} 不存在，跳过", recordId);
            return;
        }

        // 子任务：analyze
        Long analyzeTaskId = newSubTask(recordId, MakeoverConstant.STAGE_ANALYZE);
        try {
            // Stage 1
            updateRecordStatus(rec, MakeoverConstant.STATUS_ANALYZING, null, null);
            eventBus.pushStage(recordId, MakeoverConstant.STAGE_ANALYZE, "running");
            markTaskRunning(analyzeTaskId);

            String aiText = promptBuilder.analyze(rec.getOriginalUrl(),
                    rec.getSceneCode(), rec.getSceneText());
            MakeoverResultParser.ParseResult parsed = resultParser.parse(aiText);

            String faceFeaturesJson = toJson(parsed.faceFeatures());
            String suggestionsJson = toJson(parsed.suggestions());
            String summaryJson = toJson(parsed.summary());
            recordMapper.updateAiText(recordId, faceFeaturesJson, suggestionsJson, summaryJson);

            // Stage1 完成：把 AI 妆造分析结果缓存到 Redis（24h TTL），便于详情接口读穿
            cacheAiResult(recordId, faceFeaturesJson, suggestionsJson, summaryJson);

            markTaskSuccess(analyzeTaskId);

            // Stage 2：出图
            Long editTaskId = newSubTask(recordId, MakeoverConstant.STAGE_IMAGE_EDIT);
            updateRecordStatus(rec, MakeoverConstant.STATUS_EDITING, null, null);
            eventBus.pushStage(recordId, MakeoverConstant.STAGE_IMAGE_EDIT, "running");
            markTaskRunning(editTaskId);

            String editPrompt = buildImagePrompt(parsed.suggestions(), rec.getSceneCode());
            byte[] imageBytes = imageProvider.edit(rec.getOriginalUrl(), editPrompt,
                    MakeoverConstant.IMAGE_EDIT_TIMEOUT_SECONDS);
            String afterKey = buildAfterKey(rec);
            String afterUrl = fileStorage.uploadBytes(imageBytes, afterKey, "image/png");
            recordMapper.updateAfterImage(recordId, afterUrl, afterKey);

            markTaskSuccess(editTaskId);

            long cost = System.currentTimeMillis() - start;
            updateRecordStatus(rec, MakeoverConstant.STATUS_DONE, null, cost);
            eventBus.pushDone(recordId);
            log.info("[Makeover-Async] recordId={} 完成, costMs={}", recordId, cost);

        } catch (Exception e) {
            log.error("[Makeover-Async] recordId={} 失败", recordId, e);
            // 额度不足等 AI 异常统一转成中文文案，落库 + SSE 推送均使用该文案
            String msg = AiErrorMessages.toUserMessage(e);
            if (msg.length() > 480) msg = msg.substring(0, 480);
            markTaskFailed(analyzeTaskId, msg);
            updateRecordStatus(rec, MakeoverConstant.STATUS_FAILED, msg, System.currentTimeMillis() - start);
            eventBus.pushError(recordId, msg);
        }
    }

    private void updateRecordStatus(MakeoverRecord rec, byte status, String errMsg, Long costMs) {
        recordMapper.updateStatus(rec.getId(), status, errMsg, costMs);
        rec.setStatus(status);
        if (errMsg != null) rec.setErrorMessage(errMsg);
        if (costMs != null) rec.setCostMs(costMs);
    }

    private Long newSubTask(Long recordId, String stage) {
        MakeoverTask task = new MakeoverTask();
        task.setRecordId(recordId);
        task.setStage(stage);
        task.setStatus(MakeoverConstant.TASK_PENDING);
        task.setRetryCount(0);
        taskMapper.insert(task);
        return task.getId();
    }

    private void markTaskRunning(Long taskId) {
        if (taskId != null) taskMapper.markRunning(taskId, LocalDateTime.now());
    }

    private void markTaskSuccess(Long taskId) {
        if (taskId != null) taskMapper.markSuccess(taskId, LocalDateTime.now());
    }

    private void markTaskFailed(Long taskId, String msg) {
        if (taskId != null) taskMapper.markFailed(taskId, msg, LocalDateTime.now());
    }

    /**
     * 由 suggestions 拼装改造图编辑 Prompt。
     * <p>
     * 设计原则（针对 DashScope wanx2.1-imageedit / description_edit 这类"指令式编辑"模型）：
     * <ul>
     *   <li><b>身份锁定前置</b>：先讲"绝对不能改什么"，让模型先在脑子里锁住主体，再讲"要改什么"。</li>
     *   <li><b>分块结构化</b>：妆 / 发 / 衣 / 氛围 / 画质 各自一段，便于模型理解边界。</li>
     *   <li><b>句式而非罗列</b>：用"动词+对象+效果"完整句，少用顿号罗列名词。</li>
     *   <li><b>负面约束</b>：显式禁止 AI 美颜、改变脸型、改变背景等，避免 AI 脸 + 背景崩坏。</li>
     *   <li><b>写实质感</b>：用"皮肤纹理 / 毛孔 / 镜头景深"等摄影词，把模型拉向真实摄影而非插画。</li>
     * </ul>
     * protected 以便子类覆盖。
     */
    protected String buildImagePrompt(Map<String, Object> suggestions, String sceneCode) {
        // ============================================================
        // wanx2.1-imageedit / description_edit 模式 prompt 调优要点：
        //   1) 简短（< 200 字最佳），长 prompt 容易被 wanx 截断或权重稀释
        //   2) 用正向指令（"请这样做"），不要用负向禁止（"严禁/不要"）
        //   3) 摄影/美学词 wanx 不识别（"8K 高清写实人像摄影质感"）
        //   4) 多负面指令（"不要换脸/不要背景/不要美颜"）会触发 wanx 安全策略
        //
        // 之前用 5 段式强约束 prompt 反而触发了 wanx 的 instruction 解析失败，
        // 这里回归"单段短句 + 关键词"路线。
        // ============================================================

        StringBuilder sb = new StringBuilder();

        // 1) 妆造细节（用分号罗列，不加负面词）
        if (suggestions != null) {
            Object makeup = suggestions.get("makeup");
            if (makeup instanceof Map<?, ?> m) {
                appendMakeupShort(sb, m, "base");
                appendMakeupShort(sb, m, "eye");
                appendMakeupShort(sb, m, "lip");
                appendMakeupShort(sb, m, "brow");
            }
            Object hair = suggestions.get("hair");
            if (hair instanceof Map<?, ?> h) {
                appendIfPresentShort(sb, h, "style", "发型");
                appendIfPresentShort(sb, h, "color", "发色");
            }
            Object outfit = suggestions.get("outfit");
            if (outfit instanceof Map<?, ?> o) {
                appendIfPresentShort(sb, o, "style", "穿搭");
            }
        }

        // 2) 场景光感/氛围（用简短形容词）
        String sceneText = sceneNameZh(sceneCode);
        if (sb.length() > 0) sb.append("；");
        sb.append("整体光感与氛围适配「").append(sceneText).append("」场合");

        // 3) 主体保留（用正向而非负面，单句）
        sb.append("；保持原图中该人物的五官结构、脸型与肤色不变");

        String prompt = sb.toString();
        log.info("[Makeover-Async] imagePrompt: {}", prompt);
        return prompt;
    }

    /**
     * wanx 专用：妆造字段短句拼装。不同于 analyze() 阶段的 appendMakeupSentence，
     * 这里不带"让妆效自然贴合原生肤质"等冗余修饰，单字段上限 30 字截断，
     * 整体 prompt 控制在 200 字以内。
     */
    private void appendMakeupShort(StringBuilder sb, Map<?, ?> m, String key) {
        Object v = m.get(key);
        if (!(v instanceof String s) || s.isBlank()) return;
        String trimmed = s.length() > 30 ? s.substring(0, 30) + "…" : s;
        if (sb.length() > 0) sb.append("；");
        sb.append(trimmed);
    }

    /**
     * wanx 专用：通用字段短句拼装。
     */
    private void appendIfPresentShort(StringBuilder sb, Map<?, ?> map, String key, String label) {
        Object v = map.get(key);
        if (!(v instanceof String s) || s.isBlank()) return;
        String trimmed = s.length() > 30 ? s.substring(0, 30) + "…" : s;
        if (sb.length() > 0) sb.append("；");
        sb.append(label).append("：").append(trimmed);
    }

    /**
     * 场景 → 改造图色彩/光感方向（与 MakeoverPromptBuilder.buildSceneMoodHint 配套）。
     * <p>
     * wanx 对"光感"和"配色"分开响应；这里直接给画面染色方向，比抽象的"风格"标签更有效。
     */
    private String sceneMoodHint(String sceneCode) {
        String code = (sceneCode == null) ? "other" : sceneCode;
        if ("date".equals(code))    return "整体偏暖黄光感，玫瑰粉/酒红/香槟金点缀，画面柔焦微粒胶片质感";
        if ("commute".equals(code)) return "自然日光白平衡，奶茶/燕麦中性色主导，画面干净通透";
        if ("party".equals(code))   return "强聚光打亮面部高光，金属光/亮片点缀，色彩饱和度中等偏高";
        if ("travel".equals(code))  return "户外阳光暖白光，白/海军蓝主色，皮肤偏健康光泽";
        if ("wedding".equals(code)) return "柔光棚拍质感，香槟金/奶白主导，肤色均匀通透";
        if ("daily".equals(code))   return "自然日光中性白平衡，棉麻燕麦色主导，质感柔和日常";
        return "自然日光中性白平衡，基础中性配色，哑光棉麻质感";
    }

    /** 场景码 → 中文名（与 MakeoverConstant.SCENE_NAME 对齐；找不到时兜底为"日常"） */
    private String sceneNameZh(String sceneCode) {
        if (sceneCode == null) return "日常";
        return MakeoverConstant.SCENE_NAME.getOrDefault(sceneCode, "日常");
    }

    /**
     * 妆容专用拼接：每条建议单独成句（"底妆：用……，让……"），比顿号罗列更利于编辑模型理解。
     */
    private void appendMakeupSentence(StringBuilder sb, Map<?, ?> source, String key, String label) {
        Object v = source.get(key);
        if (v == null) return;
        String text = String.valueOf(v).trim();
        if (text.isEmpty()) return;
        // 句尾去分号；统一以"，让妆效自然贴合原生肤质。"收尾，避免末尾出现孤立分号
        if (sb.length() > 0) sb.append("；");
        sb.append(label).append("：").append(text).append("，让妆效自然贴合原生肤质");
    }

    private void appendIfPresent(StringBuilder sb, Map<?, ?> source, String key, String label) {
        Object v = source.get(key);
        if (v == null) return;
        String text = String.valueOf(v).trim();
        if (text.isEmpty()) return;
        if (sb.length() > 0) sb.append("；");
        sb.append(label).append("：").append(text);
    }

    private String buildAfterKey(MakeoverRecord rec) {
        String yyyymm = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        return MakeoverConstant.OSS_AFTER_PREFIX + rec.getUserId() + "/" + yyyymm + "/"
                + UUID.randomUUID().toString().replace("-", "") + ".png";
    }

    private String toJson(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            log.warn("[Makeover-Async] JSON 序列化失败: {}", e.getMessage());
            return "{}";
        }
    }

    /**
     * 将 AI 妆造分析结果（faceFeatures + suggestions + summary）写入 Redis。
     * value 结构：{"faceFeatures":{...},"suggestions":{...},"summary":{...}}
     * 失败不影响主流程（DB 已落库，详情接口会回填）。
     */
    private void cacheAiResult(Long recordId, String faceFeaturesJson, String suggestionsJson, String summaryJson) {
        try {
            Map<String, String> payload = new java.util.LinkedHashMap<>();
            payload.put("faceFeatures", faceFeaturesJson);
            payload.put("suggestions", suggestionsJson);
            payload.put("summary", summaryJson == null ? "{}" : summaryJson);
            String key = String.format(MakeoverConstant.REDIS_KEY_AI_RESULT, recordId);
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(payload),
                    MakeoverConstant.CACHE_TTL);
            log.info("[Makeover-Async] AI 分析结果已缓存 redis-key={}, ttl={}", key, MakeoverConstant.CACHE_TTL);
        } catch (Exception e) {
            log.warn("[Makeover-Async] AI 分析结果缓存失败: {}", e.getMessage());
        }
    }
}
