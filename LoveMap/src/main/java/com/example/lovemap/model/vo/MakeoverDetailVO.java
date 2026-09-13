package com.example.lovemap.model.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * AI 化妆建议详情 VO
 */
@Data
public class MakeoverDetailVO {

    private Long recordId;
    private Byte status;
    private String sceneCode;
    private String sceneText;
    private String originalUrl;
    private String afterUrl;

    /**
     * 脸型/肤质特征，结构：{faceShape, skinTone, eyeShape, lipShape, hairLength}
     */
    private Map<String, Object> faceFeatures;

    /**
     * 妆造/配饰/服装建议
     */
    private Map<String, Object> suggestions;

    /**
     * 改造总结（整体思路 + 化妆步骤详解），结构：{overall: string, steps: string[]}
     * 老数据（迁移前）此字段为 null，前端隐藏该区域。
     */
    private Map<String, Object> summary;

    /**
     * 失败原因（status=4 时有值）
     */
    private String errorMessage;

    /**
     * 总耗时（毫秒）
     */
    private Long costMs;

    private LocalDateTime createdAt;

    /**
     * 记录所有者 ID；前端用来判断当前用户是否为伴侣，从而决定是否显示「再发一次/分享给 TA/删除」等写操作按钮。
     */
    private Integer ownerId;

    /**
     * 当前用户是否为记录所有者（true=所有者本人，false=伴侣只读视角）。
     */
    private Boolean ownedByCurrent;
}