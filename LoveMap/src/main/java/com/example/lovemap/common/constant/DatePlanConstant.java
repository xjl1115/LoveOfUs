package com.example.lovemap.common.constant;

/**
 * 约会计划常量
 */
public class DatePlanConstant {

    /**
     * 状态：计划中
     */
    public static final int STATUS_PLANNED = 0;

    /**
     * 状态：已完成
     */
    public static final int STATUS_COMPLETED = 1;

    /**
     * 状态：已取消
     */
    public static final int STATUS_CANCELLED = 2;

    /**
     * 约会照片大小上限：10MB
     */
    public static final long MAX_PHOTO_SIZE_BYTES = 10 * 1024 * 1024L;

    /**
     * 约会照片存储路径前缀
     */
    public static final String OSS_PHOTO_PREFIX = "LoveMap/dateplan/photo/";

    private DatePlanConstant() {
    }
}
