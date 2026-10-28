package com.example.lovemap.service.serviceImpl;

import com.example.lovemap.common.constant.QuotaSource;
import com.example.lovemap.makeover.mapper.MakeoverQuotaGrantMapper;
import com.example.lovemap.mapper.UserMapper;
import com.example.lovemap.model.entity.User;
import com.example.lovemap.model.vo.MakeoverQuotaVO;
import com.example.lovemap.service.RateLimiterService;
import com.example.lovemap.service.VipService;
import com.example.lovemap.service.VipService.EffectiveVip;
import com.example.lovemap.makeover.mapper.MakeoverRecordMapper;
import com.example.lovemap.makeover.task.MakeoverAsyncExecutor;
import com.example.lovemap.utils.storage.FileStorage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 化妆建议次卡入账回归：购买次数不过期，月度基础额度用尽后仍有剩余。
 */
class MakeoverServiceImplTest {

    private final MakeoverRecordMapper recordMapper = mock(MakeoverRecordMapper.class);
    private final UserMapper userMapper = mock(UserMapper.class);
    private final VipService vipService = mock(VipService.class);
    private final MakeoverQuotaGrantMapper quotaGrantMapper = mock(MakeoverQuotaGrantMapper.class);
    private final MakeoverServiceImpl service = new MakeoverServiceImpl(
            recordMapper,
            mock(MakeoverAsyncExecutor.class),
            mock(FileStorage.class),
            userMapper,
            mock(RateLimiterService.class),
            mock(ObjectMapper.class),
            mock(StringRedisTemplate.class),
            vipService,
            quotaGrantMapper
    );

    @Test
    void purchasedCreditsShouldRemainAfterMonthlyAllowanceUsedUp() {
        ReflectionTestUtils.setField(service, "monthlyFreeQuota", 3);

        User user = new User();
        user.setId(9L);
        when(userMapper.selectById(9)).thenReturn(user);
        when(vipService.resolveEffectiveVip(user)).thenReturn(new EffectiveVip(0, "普通", null));
        // 本月基础额度 3 次已用完
        when(recordMapper.countBySourceInPeriod(
                eq(9), eq(QuotaSource.PERIOD), any(), any())).thenReturn(3L);
        // 累计购买 2 次，尚未使用
        when(quotaGrantMapper.sumAll(9L)).thenReturn(2);

        MakeoverQuotaVO quota = service.quota(9);

        assertEquals(false, quota.getUnlimited());
        assertEquals(3, quota.getUsed());
        assertEquals(5, quota.getTotal());
        assertEquals(2, quota.getRemaining());
    }
}
