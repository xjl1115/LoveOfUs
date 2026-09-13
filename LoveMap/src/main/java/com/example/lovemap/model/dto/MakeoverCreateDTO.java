package com.example.lovemap.model.dto;

import lombok.Data;

/**
 * AI 化妆建议创建参数
 */
@Data
public class MakeoverCreateDTO {

    /**
     * 场景编码
     */
    private String scene;

    /**
     * 场景自由文本（用户额外描述）
     */
    private String description;
}