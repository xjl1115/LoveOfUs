package com.example.lovemap.makeover.ai.impl;

import com.example.lovemap.makeover.ai.MakeoverImageProvider;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DashScope 改造图生成实现（直接 HTTP 调用 wan2.6-image 同步接口，绕过 langchain4j 与 dashscope-sdk）
 * <p>
 * 历史踩坑：
 *   1. langchain4j WanxImageModel.edit() 缺 function/base_image_url 必填字段
 *   2. dashscope-sdk 2.22.x 的 ImageSynthesis.fetch() 默认端点错误，会拿不到任务状态
 *   3. wanx2.1-imageedit 走异步 image-synthesis + task 轮询；wan2.6-image 改为同步
 *      multimodal-generation 接口，一次请求直接返回图片 URL
 * 因此完全直接用 HTTP+Jackson 调 DashScope REST API。
 * <p>
 * 接口：
 *   - POST https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation
 *     Header: Authorization=Bearer {KEY}, Content-Type=application/json
 *     Body: {"model":"wan2.6-image",
 *            "input":{"messages":[{"role":"user","content":[{"text":"..."},{"image":"..."}]}]},
 *            "parameters":{"n":1,"size":"1K","enable_interleave":false,"prompt_extend":true,"watermark":false}}
 *   - 响应：output.choices[0].message.content[*].image
 */
@Slf4j
@Component
public class DashScopeWanxImageProvider implements MakeoverImageProvider {

    private static final String GENERATION_URL =
            "https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation";

    private final String apiKey;
    private final String modelName;
    private final String size;
    private final int timeoutSeconds;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DashScopeWanxImageProvider(
            @Value("${ai.dashscope.image-edit.api-key:}") String apiKey,
            @Value("${ai.dashscope.image-edit.model-name:wan2.6-image}") String modelName,
            @Value("${ai.dashscope.image-edit.size:1K}") String size,
            @Value("${ai.dashscope.image-edit.timeout-seconds:90}") int timeoutSeconds) {
        this.apiKey = apiKey;
        this.modelName = modelName;
        this.size = size;
        this.timeoutSeconds = timeoutSeconds;
    }

    @Override
    public byte[] edit(String originalUrl, String editPrompt, int timeoutSeconds) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("ai.dashscope.image-edit.api-key 未配置");
        }
        int waitSec = timeoutSeconds > 0 ? timeoutSeconds : this.timeoutSeconds;

        log.info("[Makeover-Image] 调用 {} (HTTP 同步), prompt-len={}, baseImageUrl={}, size={}",
                modelName, editPrompt.length(), originalUrl, size);

        // 1. 构造请求体：单轮 user 消息，content 依次为 text + image
        String requestBody;
        try {
            Map<String, Object> message = new LinkedHashMap<>();
            message.put("role", "user");
            message.put("content", List.of(
                    Map.of("text", editPrompt),
                    Map.of("image", originalUrl)));

            Map<String, Object> parameters = new LinkedHashMap<>();
            // 图像编辑模式（非图文混排）；n=1 控制成本
            parameters.put("enable_interleave", false);
            parameters.put("n", 1);
            parameters.put("size", size);
            parameters.put("prompt_extend", true);
            parameters.put("watermark", false);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", modelName);
            body.put("input", Map.of("messages", List.of(message)));
            body.put("parameters", parameters);
            requestBody = objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            throw new RuntimeException("构造请求体失败: " + e.getMessage(), e);
        }

        // 2. 同步调用
        String responseBody;
        try {
            responseBody = httpPost(GENERATION_URL, requestBody, waitSec);
        } catch (IOException e) {
            throw new RuntimeException(modelName + " 调用失败: " + e.getMessage(), e);
        }
        log.info("[Makeover-Image] 响应: {}", responseBody);

        // 3. 解析图片 URL
        String outUrl;
        try {
            JsonNode json = objectMapper.readTree(responseBody);
            JsonNode content = json.path("output").path("choices").path(0).path("message").path("content");
            if (!content.isArray()) {
                String code = json.path("code").asText(null);
                String message = json.path("message").asText(null);
                throw new RuntimeException(modelName + " 响应无图像内容, code=" + code + ", message=" + message);
            }
            outUrl = null;
            for (JsonNode item : content) {
                if ("image".equals(item.path("type").asText())) {
                    outUrl = item.path("image").asText(null);
                    break;
                }
            }
            if (outUrl == null || outUrl.isBlank()) {
                throw new RuntimeException(modelName + " 响应中未找到图像: " + responseBody);
            }
        } catch (IOException e) {
            throw new RuntimeException(modelName + " 响应解析失败: " + responseBody, e);
        }
        log.info("[Makeover-Image] 改造图生成成功 url={}", outUrl);

        // 4. 下载字节
        try {
            return downloadBytes(outUrl);
        } catch (IOException e) {
            throw new RuntimeException("改造图下载失败 url=" + outUrl + " : " + e.getMessage(), e);
        }
    }

    private String httpPost(String url, String body, int readTimeoutSec) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(15_000);
            // 同步接口在服务端完成推理后才返回，读超时取调用方配置（最低 15s）
            conn.setReadTimeout(Math.max(readTimeoutSec, 15) * 1000);
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setRequestProperty("Content-Type", "application/json");
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