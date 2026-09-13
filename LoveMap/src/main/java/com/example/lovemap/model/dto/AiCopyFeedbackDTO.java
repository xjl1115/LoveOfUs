package com.example.lovemap.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * AI 文案采纳/拒绝反馈 DTO
 */
@Data
public class AiCopyFeedbackDTO {

    /**
     * Redis 缓存 key 后缀（即 AnniversaryTool 返回的 cacheKeySuffix）
     */
    @NotBlank(message = "cacheKeySuffix 不能为空")
    @Size(max = 128, message = "cacheKeySuffix 最长 128 字符")
    private String cacheKeySuffix;

    /**
     * 关联纪念日 ID，可空
     */
    private Long anniversaryId;

    /**
     * 文案风格：romantic/casual/humor/poetic
     */
    @NotBlank(message = "style 不能为空")
    @Size(max = 32, message = "style 最长 32 字符")
    private String style;

    /**
     * 反馈类型：1-采纳 2-拒绝
     */
    @NotNull(message = "feedback 不能为空")
    private Integer feedback;

    /**
     * 采纳 / 拒绝时的文案摘要（前端可传入 LLM 返回的第一条文案，便于人工复核）
     */
    @Size(max = 500, message = "copyPreview 最长 500 字符")
    private String copyPreview;
}