package com.example.lovemap.mapper;

import com.example.lovemap.model.entity.AiCopyFeedback;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * AI 纪念日文案反馈 Mapper
 */
@Mapper
public interface AiCopyFeedbackMapper {

    /**
     * 插入反馈（已存在 (user_id, cache_key_suffix) 时由应用层做 upsert）
     */
    int insert(AiCopyFeedback feedback);

    /**
     * 按 (userId, cacheKeySuffix) 查询
     */
    AiCopyFeedback selectByUserAndCacheKey(@Param("userId") Long userId,
                                           @Param("cacheKeySuffix") String cacheKeySuffix);

    /**
     * 更新反馈
     */
    int update(AiCopyFeedback feedback);

    /**
     * upsert：基于 (user_id, cache_key_suffix) 唯一键。
     * 已存在则更新 feedback/copy_preview/anniversary_id/style，
     * 不存在则插入。
     */
    int upsert(AiCopyFeedback feedback);

    /**
     * 统计某用户对某文案风格的采纳率（feedback=1 计数 / 总反馈数）。
     * 仅供 Prompt 优化离线分析使用；不暴露给 C 端。
     */
    List<StyleAcceptanceRow> selectAcceptanceByStyle(@Param("userId") Long userId);

    /**
     * Mapper 行类型：风格采纳统计
     */
    class StyleAcceptanceRow {
        private String style;
        private Integer totalCount;
        private Integer acceptedCount;

        public String getStyle() { return style; }
        public void setStyle(String style) { this.style = style; }
        public Integer getTotalCount() { return totalCount; }
        public void setTotalCount(Integer totalCount) { this.totalCount = totalCount; }
        public Integer getAcceptedCount() { return acceptedCount; }
        public void setAcceptedCount(Integer acceptedCount) { this.acceptedCount = acceptedCount; }
    }
}