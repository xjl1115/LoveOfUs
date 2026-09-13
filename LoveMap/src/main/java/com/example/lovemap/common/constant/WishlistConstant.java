package com.example.lovemap.common.constant;

/**
 * 心愿清单常量
 */
public class WishlistConstant {

    /**
     * 状态：未完成
     */
    public static final int STATUS_PENDING = 0;

    /**
     * 状态：已完成
     */
    public static final int STATUS_COMPLETED = 1;

    /**
     * 优先级：普通
     */
    public static final int PRIORITY_NORMAL = 0;

    /**
     * 优先级：高
     */
    public static final int PRIORITY_HIGH = 1;

    /**
     * 优先级：低
     */
    public static final int PRIORITY_LOW = 2;

    /**
     * 达成纪念照片大小上限：10MB
     */
    public static final long MAX_PHOTO_SIZE_BYTES = 10 * 1024 * 1024L;

    /**
     * 达成纪念照片存储路径前缀
     */
    public static final String OSS_PHOTO_PREFIX = "LoveMap/wishlist/photo/";

    /**
     * 记录卡片图片存储路径前缀
     */
    public static final String OSS_CARD_PREFIX = "LoveMap/wishlist/card/";

    /**
     * 聊天消息类型：心愿记录卡片
     */
    public static final byte MSG_TYPE_WISH_CARD = 6;

    /**
     * 卡片 extra_json 最大长度（chat_message.extra_json 为 VARCHAR(2000)，留出余量）
     */
    public static final int CARD_EXTRA_JSON_MAX = 1900;

    /**
     * extra_json 中笔记的最大字符数
     */
    public static final int CARD_NOTE_MAX = 300;

    private WishlistConstant() {
    }
}
