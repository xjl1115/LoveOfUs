package com.example.lovemap.common.constant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * VipConstant 档位额度计算单元测试
 */
class VipConstantTest {

    /** 每个账户每月免费次数（配置项 makeover.monthly-free-quota 的取值） */
    private static final int FREE = 3;

    @Test
    void makeoverMonthlyQuota_perTier() {
        assertEquals(FREE, VipConstant.makeoverMonthlyQuota(VipConstant.LEVEL_NORMAL, FREE));
        assertEquals(FREE + 2, VipConstant.makeoverMonthlyQuota(VipConstant.LEVEL_WEEK, FREE));
        assertEquals(FREE + 6, VipConstant.makeoverMonthlyQuota(VipConstant.LEVEL_MONTH, FREE));
        assertEquals(FREE + 9, VipConstant.makeoverMonthlyQuota(VipConstant.LEVEL_QUARTER, FREE));
        assertEquals(FREE + 13, VipConstant.makeoverMonthlyQuota(VipConstant.LEVEL_YEAR, FREE));
        assertNull(VipConstant.makeoverMonthlyQuota(VipConstant.LEVEL_FOREVER, FREE));
    }

    @Test
    void makeoverMonthlyQuota_unknownLevelFallsBackToFree() {
        assertEquals(FREE, VipConstant.makeoverMonthlyQuota(99, FREE));
        assertEquals(FREE, VipConstant.makeoverMonthlyQuota(null, FREE));
    }

    @Test
    void makeoverQuotaText_totalOrUnlimited() {
        assertEquals("AI 化妆建议 5 次/月", VipConstant.makeoverQuotaText(VipConstant.LEVEL_WEEK, FREE));
        assertEquals("AI 化妆建议 12 次/月", VipConstant.makeoverQuotaText(VipConstant.LEVEL_QUARTER, FREE));
        assertEquals("AI 化妆建议不限次", VipConstant.makeoverQuotaText(VipConstant.LEVEL_FOREVER, FREE));
    }
}
