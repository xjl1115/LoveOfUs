package com.example.lovemap.common.constant;

/**
 * 相册模块常量
 */
public final class AlbumConstant {

    private AlbumConstant() {
    }

    /**
     * 相册列表 Redis KEY 前缀
     * 格式：album:list:{groupId} 或 album:list:{userId}
     * 有 groupId 时用 groupId，否则用 userId
     */
    public static final String ALBUM_LIST_PREFIX = "album:list:";

    /**
     * 相册详情 Redis KEY 前缀
     * 格式：album:detail:{albumId}:{page}:{size}
     * 清除时使用 album:detail:{albumId}:* 匹配删除
     */
    public static final String ALBUM_DETAIL_PREFIX = "album:detail:";
}
