package com.example.lovemap.model.vo;

import lombok.Data;

/**
 * AI 化妆建议创建响应
 */
@Data
public class MakeoverCreateVO {

    /**
     * 记录 ID
     */
    private Long recordId;

    /**
     * 当前状态：0=待处理
     */
    private Byte status;

    /**
     * 原图 OSS 访问 URL
     */
    private String originalUrl;
}