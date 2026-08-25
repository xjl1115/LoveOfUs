package com.example.lovemap.common.constant;

/**
 * 纪念日模块常量
 */
public final class AnniversaryConstant {

    private AnniversaryConstant() {
    }

    /**
     * 纪念日列表 Redis KEY 前缀
     * 格式：anniversary:list:{groupId} 或 anniversary:list:{userId}
     * 有 groupId 时用 groupId，否则用 userId
     */
    public static final String ANNIVERSARY_LIST_PREFIX = "anniversary:list:";

    /**
     * 纪念日详情 Redis KEY 前缀
     * 格式：anniversary:detail:{id}
     */
    public static final String ANNIVERSARY_DETAIL_PREFIX = "anniversary:detail:";
}