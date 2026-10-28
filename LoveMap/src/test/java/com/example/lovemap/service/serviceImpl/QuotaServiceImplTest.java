package com.example.lovemap.service.serviceImpl;

import com.example.lovemap.common.constant.QuotaType;
import com.example.lovemap.common.constant.QuotaSource;
import com.example.lovemap.mapper.QuotaLogMapper;
import com.example.lovemap.mapper.VipOrderMapper;
import com.example.lovemap.model.entity.QuotaLog;
import com.example.lovemap.model.entity.User;
import com.example.lovemap.service.VipService;
import com.example.lovemap.service.VipService.EffectiveVip;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 次卡入账回归：入账次数按"已开通次卡订单"累计，不写 quota_log 使用流水；
 * 购买次数不过期（用完为止），先扣周期基础额度，基础额度用尽后才扣次卡。
 */
class QuotaServiceImplTest {

    private final QuotaLogMapper quotaLogMapper = mock(QuotaLogMapper.class);
    private final VipOrderMapper vipOrderMapper = mock(VipOrderMapper.class);
    private final VipService vipService = mock(VipService.class);
    private final QuotaServiceImpl service =
            new QuotaServiceImpl(quotaLogMapper, vipOrderMapper, vipService);

    @Test
    void purchasedQuotaShouldRaiseRemainingWhenPeriodAllowanceUsedUp() {
        ReflectionTestUtils.setField(service, "aiBase", 3);

        User user = user(9L);
        when(vipService.resolveEffectiveVip(user)).thenReturn(new EffectiveVip(0, "普通", null));
        // 本周期基础额度 3 次已用完
        when(quotaLogMapper.countBySourceInPeriod(
                eq(9L), eq(QuotaType.AI_RECOMMEND), eq(QuotaSource.PERIOD), any(), any()))
                .thenReturn(3L);
        // 累计购买 5 次，历史上已用掉 2 次次卡（可能发生在上个周期）
        when(vipOrderMapper.sumActivatedQuotaCount(eq(9L), eq(2))).thenReturn(5);
        when(quotaLogMapper.countBySource(
                eq(9L), eq(QuotaType.AI_RECOMMEND), eq(QuotaSource.PURCHASED)))
                .thenReturn(2L);

        assertEquals(6, service.limit(user, QuotaType.AI_RECOMMEND));
        assertEquals(3, service.remaining(user, QuotaType.AI_RECOMMEND));
    }

    @Test
    void consumeShouldUsePurchasedQuotaOnlyAfterPeriodAllowanceUsedUp() {
        ReflectionTestUtils.setField(service, "aiBase", 3);

        User user = user(9L);
        when(vipService.resolveEffectiveVip(user)).thenReturn(new EffectiveVip(0, "普通", null));
        when(quotaLogMapper.countBySourceInPeriod(
                eq(9L), eq(QuotaType.AI_RECOMMEND), eq(QuotaSource.PERIOD), any(), any()))
                .thenReturn(3L);
        when(vipOrderMapper.sumActivatedQuotaCount(eq(9L), eq(2))).thenReturn(5);

        service.consume(user, QuotaType.AI_RECOMMEND);

        ArgumentCaptor<QuotaLog> captor = ArgumentCaptor.forClass(QuotaLog.class);
        verify(quotaLogMapper).insert(captor.capture());
        assertEquals(QuotaSource.PURCHASED, captor.getValue().getQuotaSource());
    }

    @Test
    void consumeShouldUsePeriodAllowanceFirst() {
        ReflectionTestUtils.setField(service, "aiBase", 3);

        User user = user(9L);
        when(vipService.resolveEffectiveVip(user)).thenReturn(new EffectiveVip(0, "普通", null));
        when(quotaLogMapper.countBySourceInPeriod(
                eq(9L), eq(QuotaType.AI_RECOMMEND), eq(QuotaSource.PERIOD), any(), any()))
                .thenReturn(0L);

        service.consume(user, QuotaType.AI_RECOMMEND);

        ArgumentCaptor<QuotaLog> captor = ArgumentCaptor.forClass(QuotaLog.class);
        verify(quotaLogMapper).insert(captor.capture());
        assertEquals(QuotaSource.PERIOD, captor.getValue().getQuotaSource());
    }

    @Test
    void exportQuotaShouldNotCountQuotaCardOrders() {
        ReflectionTestUtils.setField(service, "exportBase", 3);

        User user = user(9L);
        when(vipService.resolveEffectiveVip(user)).thenReturn(new EffectiveVip(0, "普通", null));

        assertEquals(3, service.limit(user, QuotaType.EXPORT));
        verify(vipOrderMapper, never()).sumActivatedQuotaCount(any(), any());
    }

    private static User user(Long id) {
        User user = new User();
        user.setId(id);
        return user;
    }
}
