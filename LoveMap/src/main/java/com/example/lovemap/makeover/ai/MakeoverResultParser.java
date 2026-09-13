package com.example.lovemap.makeover.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Qwen-VL 返回的 JSON 解析器
 */
@Slf4j
@Component
public class MakeoverResultParser {

    private static final List<String> FACE_FIELDS = List.of(
            "faceShape", "skinTone", "eyeShape", "lipShape", "hairLength");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 解析 AI 返回文本，提取 faceFeatures 与 suggestions
     *
     * @param aiText Qwen-VL 响应文本
     * @return ParseResult，包含 faceFeatures 与 suggestions（均为 Map）
     * @throws IllegalArgumentException 解析失败时抛出
     */
    public ParseResult parse(String aiText) {
        if (aiText == null || aiText.isBlank()) {
            throw new IllegalArgumentException("AI 返回文本为空");
        }

        String json = extractJson(aiText);
        if (json == null) {
            throw new IllegalArgumentException("AI 返回文本未包含 JSON: " + truncate(aiText));
        }

        JsonNode root;
        try {
            root = MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("AI 返回的 JSON 无法解析: " + e.getMessage());
        }

        if (root == null || root.isMissingNode() || root.isNull()) {
            throw new IllegalArgumentException("AI 返回的 JSON 根节点为空");
        }

        Map<String, Object> faceFeatures = new LinkedHashMap<>();
        for (String field : FACE_FIELDS) {
            JsonNode node = root.path("faceFeatures").path(field);
            faceFeatures.put(field, node.isMissingNode() || node.isNull() ? "" : node.asText());
        }

        JsonNode suggestionsNode = root.path("suggestions");
        Map<String, Object> suggestions;
        if (suggestionsNode.isMissingNode() || suggestionsNode.isNull()) {
            suggestions = new LinkedHashMap<>();
        } else {
            suggestions = MAPPER.convertValue(suggestionsNode, Map.class);
        }

        // 若 suggestions 为空对象，视为解析失败
        if (suggestions.isEmpty()) {
            throw new IllegalArgumentException("AI 返回的 suggestions 为空");
        }

        // summary 字段（改造总结）：可缺失——AI 老版本/失败时降级为空 Map，UI 隐藏该区域即可
        Map<String, Object> summary = new LinkedHashMap<>();
        JsonNode summaryNode = root.path("summary");
        if (!summaryNode.isMissingNode() && !summaryNode.isNull() && summaryNode.isObject()) {
            summary = MAPPER.convertValue(summaryNode, Map.class);
        }
        // steps 必为 List<String>，前端按数组渲染
        if (!(summary.get("steps") instanceof List<?>)) {
            summary.put("steps", summary.get("steps") == null ? List.of() : List.of(String.valueOf(summary.get("steps"))));
        }

        log.info("[Makeover-Parse] 解析成功, faceFeatures={}, suggestionsKeys={}, summaryKeys={}",
                faceFeatures, suggestions.keySet(), summary.keySet());
        return new ParseResult(faceFeatures, suggestions, summary);
    }

    /**
     * 从可能包含 Markdown / 前后缀的文本中提取第一个 JSON 对象
     */
    String extractJson(String text) {
        if (text == null) return null;
        String trimmed = text.trim();

        // 1. 直接是 JSON
        if (trimmed.startsWith("{")) {
            return extractBalancedJson(trimmed, 0);
        }

        // 2. 去掉 markdown ```json ... ``` 包裹
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            if (firstNewline > 0) {
                String body = trimmed.substring(firstNewline + 1);
                int end = body.lastIndexOf("```");
                if (end > 0) body = body.substring(0, end);
                body = body.trim();
                if (body.startsWith("{")) {
                    return extractBalancedJson(body, 0);
                }
            }
        }

        // 3. 在任意位置寻找第一个 '{'
        int braceIdx = trimmed.indexOf('{');
        if (braceIdx >= 0) {
            return extractBalancedJson(trimmed, braceIdx);
        }

        return null;
    }

    /**
     * 从 start 起寻找一个平衡的 JSON 对象子串
     */
    String extractBalancedJson(String text, int start) {
        int depth = 0;
        boolean inString = false;
        boolean escape = false;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (escape) {
                escape = false;
                continue;
            }
            if (c == '\\' && inString) {
                escape = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                continue;
            }
            if (inString) continue;

            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return text.substring(start, i + 1);
                }
            }
        }
        return null;
    }

    private String truncate(String s) {
        if (s == null) return "";
        return s.length() > 200 ? s.substring(0, 200) + "..." : s;
    }

    /**
     * 解析结果
     */
    public record ParseResult(
            Map<String, Object> faceFeatures,
            Map<String, Object> suggestions,
            Map<String, Object> summary) {
    }
}