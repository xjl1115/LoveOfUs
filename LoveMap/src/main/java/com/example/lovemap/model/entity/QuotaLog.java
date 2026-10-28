package com.example.lovemap.model.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 配额使用流水
 */
@Data
public class QuotaLog {
    private Long id;
    private Long userId;
    private Integer quotaType;
    /** 消耗来源：1=周期基础额度，2=次卡入账（不过期） */
    private Integer quotaSource;
    private LocalDateTime usedAt;
}
