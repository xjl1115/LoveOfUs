package com.example.lovemap.common.constant;

/**
 * 照片模块常量
 */
public final class PhotoConstant {

    private PhotoConstant() {
    }

    /**
     * 照片详情 Redis KEY 前缀
     * 格式：photo:detail:{photoId}
     */
    public static final String PHOTO_DETAIL_PREFIX = "photo:detail:";

    /**
     * 照片上传分布式锁 Redis KEY 前缀
     * 格式：upload:lock:{albumId}:{userId}
     */
    public static final String UPLOAD_LOCK_KEY_PREFIX = "upload:lock:";

    /**
     * 照片上传分布式锁超时时间（秒）
     */
    public static final long UPLOAD_LOCK_TIMEOUT = 30;
}
