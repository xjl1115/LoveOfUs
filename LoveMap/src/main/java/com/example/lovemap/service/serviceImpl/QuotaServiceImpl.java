package com.example.lovemap.service.serviceImpl;

import com.example.lovemap.common.BusinessException;
import com.example.lovemap.common.ResultCode;
import com.example.lovemap.common.constant.QuotaProductMapping;
import com.example.lovemap.common.constant.QuotaSource;
import com.example.lovemap.common.constant.QuotaType;
import com.example.lovemap.common.constant.VipConstant;
import com.example.lovemap.mapper.QuotaLogMapper;
import com.example.lovemap.mapper.VipOrderMapper;
import com.example.lovemap.model.entity.QuotaLog;
import com.example.lovemap.model.entity.User;
import com.example.lovemap.service.QuotaService;
import com.example.lovemap.service.VipService;
import com.example.lovemap.service.VipService.EffectiveVip;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;

@Slf4j
@Service
public class QuotaServiceImpl implements QuotaService {

    /** 永久卡不限量，统一用 -1 表示 */
    private static final int UNLIMITED = -1;

    private final QuotaLogMapper quotaLogMapper;
    /**
     * 次卡入账来源：vip_order（status=1 的已开通次卡订单），不过期，用完为止。
     */
    private final VipOrderMapper vipOrderMapper;
    /**
     * 与 VipServiceImpl 存在间接循环依赖，用 {@link Lazy} 注入代理对象避免启动失败。
     */
    private final VipService vipService;

    public QuotaServiceImpl(QuotaLogMapper quotaLogMapper,
                            VipOrderMapper vipOrderMapper,
                            @Lazy VipService vipService) {
        this.quotaLogMapper = quotaLogMapper;
        this.vipOrderMapper = vipOrderMapper;
        this.vipService = vipService;
    }

    @Value("${quota.export.base-monthly:3}")
    private int exportBase;

    @Value("${quota.ai-recommend.base-weekly:3}")
    private int aiBase;

    @Value("${quota.vip-bonus.week:2}")
    private int weekBonus;

    @Value("${quota.vip-bonus.month:6}")
    private int monthBonus;

    @Value("${quota.vip-bonus.quarter:9}")
    private int quarterBonus;

    @Value("${quota.vip-bonus.year:12}")
    private int yearBonus;

    @Override
    public void consume(User user, int quotaType) {
        EffectiveVip vip = vipService.resolveEffectiveVip(user);
        int source;
        if (vip.level() == VipConstant.LEVEL_FOREVER) {
            // 永久卡不限量，不消耗次卡
            source = QuotaSource.PERIOD;
        } else if (periodRemaining(vip, user, quotaType) > 0) {
            source = QuotaSource.PERIOD;
        } else if (purchasedRemaining(user, quotaType) > 0) {
            source = QuotaSource.PURCHASED;
        } else {
            throw new BusinessException(ResultCode.FORBIDDEN, buildExhaustedMessage(quotaType));
        }
        QuotaLog log = new QuotaLog();
        log.setUserId(user.getId());
        log.setQuotaType(quotaType);
        log.setQuotaSource(source);
        log.setUsedAt(LocalDateTime.now());
        quotaLogMapper.insert(log);
    }

    @Override
    public int remaining(User user, int quotaType) {
        EffectiveVip vip = vipService.resolveEffectiveVip(user);
        if (vip.level() == VipConstant.LEVEL_FOREVER) {
            return Integer.MAX_VALUE;
        }
        return periodRemaining(vip, user, quotaType) + purchasedRemaining(user, quotaType);
    }

    @Override
    public int limit(User user, int quotaType) {
        EffectiveVip vip = vipService.resolveEffectiveVip(user);
        if (vip.level() == VipConstant.LEVEL_FOREVER) {
            return UNLIMITED;
        }
        // 分母 = 本周期基础额度 + 未用完的次卡次数，保证 已用 = 额度 - 剩余 仍等于本周期消耗
        return periodAllowance(vip, quotaType) + purchasedRemaining(user, quotaType);
    }

    /** 本周期基础额度：基础次数 + VIP 档位加成，周一/月初重置 */
    private int periodAllowance(EffectiveVip vip, int quotaType) {
        int base = (quotaType == QuotaType.EXPORT) ? exportBase : aiBase;
        return base + vipBonus(vip.level());
    }

    /** 本周期基础额度剩余（只统计来源为基础额度的消耗） */
    private int periodRemaining(EffectiveVip vip, User user, int quotaType) {
        long used = quotaLogMapper.countBySourceInPeriod(
                user.getId(), quotaType, QuotaSource.PERIOD,
                periodStart(quotaType), periodEnd(quotaType));
        return (int) Math.max(0L, periodAllowance(vip, quotaType) - used);
    }

    /**
     * 次卡剩余次数 = 累计已开通次卡次数 - 累计消耗的次卡次数。
     * <p>
     * 入账来自 vip_order（已开通 = status 1），不过期、不随周期重置，用完为止。
     */
    private int purchasedRemaining(User user, int quotaType) {
        int granted = purchasedTotal(user, quotaType);
        if (granted <= 0) {
            return 0;
        }
        long used = quotaLogMapper.countBySource(user.getId(), quotaType, QuotaSource.PURCHASED);
        return (int) Math.max(0L, granted - used);
    }

    /** 累计已开通次卡次数 */
    private int purchasedTotal(User user, int quotaType) {
        int productType = QuotaProductMapping.toProductType(quotaType);
        if (productType < 0) {
            return 0;
        }
        return vipOrderMapper.sumActivatedQuotaCount(user.getId(), productType);
    }

    private int vipBonus(int level) {
        return switch (level) {
            case VipConstant.LEVEL_WEEK -> weekBonus;
            case VipConstant.LEVEL_MONTH -> monthBonus;
            case VipConstant.LEVEL_QUARTER -> quarterBonus;
            case VipConstant.LEVEL_YEAR -> yearBonus;
            default -> 0;
        };
    }

    /** 自然月：当前月 1 号 00:00 ~ 下月 1 号 00:00 */
    private LocalDateTime periodStart(int quotaType) {
        if (quotaType == QuotaType.EXPORT) {
            return LocalDateTime.of(LocalDate.now().withDayOfMonth(1), LocalTime.MIN);
        }
        // 周一 00:00 (Asia/Shanghai)
        LocalDate monday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return LocalDateTime.of(monday, LocalTime.MIN);
    }

    private LocalDateTime periodEnd(int quotaType) {
        if (quotaType == QuotaType.EXPORT) {
            return periodStart(quotaType).plusMonths(1);
        }
        return periodStart(quotaType).plusWeeks(1);
    }

    private String buildExhaustedMessage(int quotaType) {
        String scope = (quotaType == QuotaType.EXPORT) ? "导出与备份" : "AI 推荐";
        return scope + "本周期额度已用完，可升级 VIP 获取更多次数或单独购买次数包";
    }
}
