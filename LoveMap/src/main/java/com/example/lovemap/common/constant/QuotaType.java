package com.example.lovemap.common.constant;

/**
 * 配额类型枚举
 */
public final class QuotaType {

    private QuotaType() {}

    /** 导出与备份（每月） */
    public static final int EXPORT = 1;

    /** AI 推荐 - 约会（每周） */
    public static final int AI_RECOMMEND = 2;

    /** AI 推荐 - 心愿（每周） */
    public static final int AI_WISHLIST = 3;
}
