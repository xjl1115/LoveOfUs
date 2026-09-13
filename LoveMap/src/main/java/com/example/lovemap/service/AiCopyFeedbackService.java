package com.example.lovemap.service;

import com.example.lovemap.common.Result;
import com.example.lovemap.model.dto.AiCopyFeedbackDTO;

import java.util.List;

/**
 * AI 文案反馈服务接口
 */
public interface AiCopyFeedbackService {

    /**
     * 提交文案采纳 / 拒绝反馈。同 (userId, cacheKeySuffix) 多次提交以最后一次为准。
     */
    Result<Void> submitFeedback(Integer userId, AiCopyFeedbackDTO dto);

    /**
     * 统计某用户对各风格的采纳率，供 Prompt 优化离线分析使用。
     */
    Result<List<StyleAcceptanceVO>> getAcceptanceStats(Integer userId);

    /**
     * 风格采纳率 VO
     */
    class StyleAcceptanceVO {
        private String style;
        private Integer totalCount;
        private Integer acceptedCount;
        private Double acceptanceRate;

        public String getStyle() { return style; }
        public void setStyle(String style) { this.style = style; }
        public Integer getTotalCount() { return totalCount; }
        public void setTotalCount(Integer totalCount) { this.totalCount = totalCount; }
        public Integer getAcceptedCount() { return acceptedCount; }
        public void setAcceptedCount(Integer acceptedCount) { this.acceptedCount = acceptedCount; }
        public Double getAcceptanceRate() { return acceptanceRate; }
        public void setAcceptanceRate(Double acceptanceRate) { this.acceptanceRate = acceptanceRate; }
    }
}