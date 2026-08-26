package com.example.lovemap.ai.tool;

import com.example.lovemap.ai.context.AiUserContext;
import com.example.lovemap.common.Result;
import com.example.lovemap.model.vo.AlbumVO;
import com.example.lovemap.service.AlbumService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 相册 AI 工具
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlbumTool {

    private final AlbumService albumService;

    /**
     * 查询所有相册
     */
    @Tool("查询当前用户的所有相册（不含照片内容）。")
    public List<Map<String, Object>> listAlbums() {
        Long userId = AiUserContext.requireUserId();
        log.info("[AI-TOOL] listAlbums userId={}", userId);
        try {
            Result<List<AlbumVO>> result = albumService.listAlbums(userId.intValue());
            if (result == null || result.getData() == null) return List.of();
            List<Map<String, Object>> out = new ArrayList<>();
            for (AlbumVO vo : result.getData()) {
                out.add(toMap(vo));
            }
            return out;
        } catch (Exception e) {
            log.error("[AI-TOOL] listAlbums 失败", e);
            return List.of(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 按名称搜索相册（模糊匹配）
     */
    @Tool("按名称模糊搜索相册，例如：'三亚'、'婚礼'。")
    public List<Map<String, Object>> searchAlbumByName(@P("相册名称关键词") String keyword) {
        Long userId = AiUserContext.requireUserId();
        log.info("[AI-TOOL] searchAlbumByName userId={}, keyword='{}'", userId, keyword);
        try {
            Result<List<AlbumVO>> result = albumService.listAlbums(userId.intValue());
            if (result == null || result.getData() == null) return List.of();
            String kw = (keyword == null ? "" : keyword.trim().toLowerCase());
            List<Map<String, Object>> out = new ArrayList<>();
            for (AlbumVO vo : result.getData()) {
                if (kw.isEmpty() || (vo.getName() != null && vo.getName().toLowerCase().contains(kw))) {
                    out.add(toMap(vo));
                }
            }
            return out;
        } catch (Exception e) {
            log.error("[AI-TOOL] searchAlbumByName 失败", e);
            return List.of(Map.of("error", e.getMessage()));
        }
    }

    // ==================== 内部 ====================

    private Map<String, Object> toMap(AlbumVO vo) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", vo.getId());
        map.put("name", vo.getName());
        map.put("description", vo.getDescription());
        map.put("coverUrl", vo.getCoverPhotoUrl());
        map.put("photoCount", vo.getPhotoCount());
        map.put("createdAt", vo.getCreatedAt() != null ? vo.getCreatedAt().toString() : null);
        return map;
    }
}