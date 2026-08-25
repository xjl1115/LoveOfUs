package com.example.lovemap.common.constant;

/**
 * 导出模块常量
 */
public final class ExportConstant {

    private ExportConstant() {
    }

    /**
     * 导出分布式锁 Redis KEY 前缀
     * 格式：export:lock:{userId}
     */
    public static final String EXPORT_LOCK_KEY_PREFIX = "export:lock:";

    /**
     * 导出分布式锁超时时间（秒）
     * 导出是耗时操作，设为 600 秒（10分钟）
     */
    public static final long EXPORT_LOCK_TIMEOUT = 600;
}