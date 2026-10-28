package com.example.lovemap.model.vo;

import lombok.Data;

/**
 * 用户当前周期配额快照
 */
@Data
public class VipQuotaVO {

    /** AI 约会推荐 */
    private QuotaItem aiRecommend;
    /** AI 心愿推荐 */
    private QuotaItem aiWishlist;
    /** 数据备份与导出 */
    private QuotaItem export;

    @Data
    public static class QuotaItem {
        /** 已使用次数 */
        private int used;
        /** 当前周期上限，-1 表示不限量（永久卡） */
        private int limit;
        /** 剩余次数 */
        private int remaining;
        /** 是否不限量 */
        private boolean unlimited;
    }
}
