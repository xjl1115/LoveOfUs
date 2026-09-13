package com.example.lovemap.makeover.ai.impl;

import com.example.lovemap.makeover.ai.MakeoverImageProvider;
import com.example.lovemap.makeover.constant.MakeoverConstant;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * DashScope 改造图生成实现（直接 HTTP 调用 wanx2.1-imageedit，绕过 langchain4j 与 dashscope-sdk）
 * <p>
 * 历史踩坑：
 *   1. langchain4j WanxImageModel.edit() 缺 function/base_image_url 必填字段
 *   2. dashscope-sdk 2.22.x 的 ImageSynthesis.fetch() 默认端点错误，会拿不到任务状态
 *   3. description_edit_all（wanx2.0 字段名）在 wanx2.1 改成 description_edit
 * 因此完全直接用 HTTP+Jackson 调 DashScope REST API。
 * <p>
 * 接口：
 *   - 提交：POST https://dashscope.aliyuncs.com/api/v1/services/aigc/image2image/image-synthesis
 *     Header: Authorization=Bearer {KEY}, X-DashScope-Async=enable, Content-Type=application/json
 *     Body: {"model":"wanx2.1-imageedit","input":{"function":"description_edit","prompt":"...","base_image_url":"..."},"parameters":{"n":1,"size":"1024*1024"}}
 *   - 查询：GET https://dashscope.aliyuncs.com/api/v1/tasks/{task_id}
 *     Header: Authorization=Bearer {KEY}
 */
@Slf4j
@Component
public class DashScopeWanxImageProvider implements MakeoverImageProvider {

    private static final String SUBMIT_URL = "https://dashscope.aliyuncs.com/api/v1/services/aigc/image2image/image-synthesis";
    private static final String QUERY_URL_PREFIX = "https://dashscope.aliyuncs.com/api/v1/tasks/";
    private static final long POLL_INTERVAL_MS = 1_500L;

    private final String apiKey;
    private final String modelName;
    private final int timeoutSeconds;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DashScopeWanxImageProvider(
            @Value("${ai.dashscope.image-edit.api-key:}") String apiKey,
            @Value("${ai.dashscope.image-edit.model-name:wanx2.1-imageedit}") String modelName,
            // 以下两个字段被忽略（dashscope-sdk 原生字段），保留仅为兼容旧配置
            @Value("${ai.dashscope.image-edit.style:#{null}}") String styleIgnored,
            @Value("${ai.dashscope.image-edit.ref-mode:#{null}}") String refModeIgnored,
            @Value("${ai.dashscope.image-edit.size:1024*1024}") String sizeIgnored,
            @Value("${ai.dashscope.image-edit.timeout-seconds:90}") int timeoutSeconds) {
        this.apiKey = apiKey;
        this.modelName = modelName;
        this.timeoutSeconds = timeoutSeconds;
    }

    @Override
    public byte[] edit(String originalUrl, String editPrompt, int timeoutSeconds) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("ai.dashscope.image-edit.api-key 未配置");
        }
        int waitSec = timeoutSeconds > 0 ? timeoutSeconds : this.timeoutSeconds;

        log.info("[Makeover-Image] 调用 wanx2.1-imageedit (HTTP), model={}, prompt-len={}, baseImageUrl={}",
                modelName, editPrompt.length(), originalUrl);

        // 1. 提交任务
        String submitBody;
        try {
            Map<String, Object> input = new HashMap<>();
            input.put("function", MakeoverConstant.IMAGE_EDIT_FUNCTION); // description_edit
            input.put("prompt", editPrompt);
            input.put("base_image_url", originalUrl);
            Map<String, Object> params = new HashMap<>();
            params.put("n", 1);
            Map<String, Object> body = new HashMap<>();
            body.put("model", modelName);
            body.put("input", input);
            body.put("parameters", params);
            submitBody = objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            throw new RuntimeException("构造请求体失败: " + e.getMessage(), e);
        }

        String submitResp;
        try {
            submitResp = httpPost(SUBMIT_URL, submitBody);
        } catch (IOException e) {
            throw new RuntimeException("wanx 提交失败: " + e.getMessage(), e);
        }
        log.info("[Makeover-Image] 提交响应: {}", submitResp);

        String taskId;
        try {
            JsonNode submitJson = objectMapper.readTree(submitResp);
            taskId = submitJson.path("output").path("task_id").asText(null);
            if (taskId == null || taskId.isBlank()) {
                String code = submitJson.path("code").asText(null);
                String message = submitJson.path("message").asText(null);
                throw new RuntimeException("wanx 提交响应无 task_id, code=" + code + ", message=" + message);
            }
        } catch (IOException e) {
            throw new RuntimeException("wanx 提交响应解析失败: " + submitResp, e);
        }
        log.info("[Makeover-Image] 任务已提交 taskId={}", taskId);

        // 2. 轮询
        long deadline = System.currentTimeMillis() + waitSec * 1000L;
        JsonNode outputNode;
        String taskStatus;
        while (true) {
            String queryResp;
            try {
                queryResp = httpGet(QUERY_URL_PREFIX + taskId);
            } catch (IOException e) {
                throw new RuntimeException("wanx 查询失败: " + e.getMessage(), e);
            }
            try {
                JsonNode queryJson = objectMapper.readTree(queryResp);
                outputNode = queryJson.path("output");
                taskStatus = outputNode.path("task_status").asText(null);
            } catch (IOException e) {
                throw new RuntimeException("wanx 查询响应解析失败: " + queryResp, e);
            }
            log.info("[Makeover-Image] poll taskStatus={}, full={}", taskStatus, outputNode);

            if ("SUCCEEDED".equalsIgnoreCase(taskStatus)) {
                break;
            }
            if ("FAILED".equalsIgnoreCase(taskStatus) || "CANCELED".equalsIgnoreCase(taskStatus)) {
                String code = outputNode.path("code").asText(null);
                String message = outputNode.path("message").asText(null);
                log.error("[Makeover-Image] wanx 任务失败 status={}, baseImageUrl={}, code={}, message={}",
                        taskStatus, originalUrl, code, message);
                throw new RuntimeException("wanx 任务失败 status=" + taskStatus + ", code=" + code + ", message=" + message);
            }
            if (System.currentTimeMillis() > deadline) {
                throw new RuntimeException("wanx 任务超时 " + waitSec + "s, 当前 status=" + taskStatus);
            }
            try {
                Thread.sleep(POLL_INTERVAL_MS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("wanx 轮询被中断", ie);
            }
        }

        // 3. 提取 URL
        JsonNode resultsNode = outputNode.path("results");
        if (!resultsNode.isArray() || resultsNode.isEmpty()) {
            throw new RuntimeException("wanx 结果为空 output=" + outputNode);
        }
        String outUrl = resultsNode.get(0).path("url").asText(null);
        if (outUrl == null || outUrl.isBlank()) {
            throw new RuntimeException("wanx 结果无 url field");
        }
        log.info("[Makeover-Image] 改造图生成成功 url={}", outUrl);

        // 4. 下载字节
        try {
            return downloadBytes(outUrl);
        } catch (IOException e) {
            throw new RuntimeException("改造图下载失败 url=" + outUrl + " : " + e.getMessage(), e);
        }
    }

    private String httpPost(String url, String body) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(15_000);
            conn.setReadTimeout(60_000);
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("X-DashScope-Async", "enable");
            conn.setDoOutput(true);
            conn.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
            int code = conn.getResponseCode();
            try (InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream()) {
                byte[] bytes = is.readAllBytes();
                String resp = new String(bytes, StandardCharsets.UTF_8);
                if (code >= 400) {
                    throw new IOException("HTTP " + code + ": " + resp);
                }
                return resp;
            }
        } finally {
            conn.disconnect();
        }
    }

    private String httpGet(String url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        try {
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(15_000);
            conn.setReadTimeout(60_000);
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            int code = conn.getResponseCode();
            try (InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream()) {
                byte[] bytes = is.readAllBytes();
                String resp = new String(bytes, StandardCharsets.UTF_8);
                if (code >= 400) {
                    throw new IOException("HTTP " + code + ": " + resp);
                }
                return resp;
            }
        } finally {
            conn.disconnect();
        }
    }

    private byte[] downloadBytes(String url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        conn.setConnectTimeout(15_000);
        conn.setReadTimeout(60_000);
        try (InputStream in = conn.getInputStream()) {
            return in.readAllBytes();
        } finally {
            conn.disconnect();
        }
    }
}