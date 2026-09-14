package com.example.lovemap.common.constant;

/**
 * 纪念日模块常量
 */
public final class AnniversaryConstant {

    private AnniversaryConstant() {
    }

    /**
     * 纪念日列表 Redis KEY 前缀
     * 格式：anniversary:list:g{groupId}（已绑定情侣，组内共享）
     *       anniversary:list:u{userId}（未绑定情侣，个人数据）
     * 加 g/u 前缀是为了避免组 ID 与用户 ID 撞号导致缓存串号
     */
    public static final String ANNIVERSARY_LIST_PREFIX = "anniversary:list:";

    /**
     * 纪念日详情 Redis KEY 前缀
     * 格式：anniversary:detail:{id}
     */
    public static final String ANNIVERSARY_DETAIL_PREFIX = "anniversary:detail:";

    /**
     * 纪念日提醒"当日已执行"标记 KEY，值为 yyyy-MM-dd
     * 用于启动补跑时判断当天 0 点的提醒是否已经跑过
     */
    public static final String REMIND_LAST_RUN_KEY = "anniversary:remind:last-run-date";
}
