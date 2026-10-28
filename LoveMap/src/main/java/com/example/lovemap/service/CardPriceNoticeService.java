package com.example.lovemap.service;

import com.example.lovemap.common.ServiceHelper;
import com.example.lovemap.common.constant.NotificationConstant;
import com.example.lovemap.common.constant.UserConstant;
import com.example.lovemap.mapper.UserMapper;
import com.example.lovemap.model.entity.User;
import com.example.lovemap.model.entity.VipPricing;
import com.example.lovemap.model.vo.NotificationSettingsVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 卡类价格变更通知服务
 * <p>
 * 顾问在后台（vip-admin.html）调整 vip_pricing 中的 VIP 卡 / 次卡价格后，向全体用户广播系统通知；
 * 用户在“消息通知设置”中勾选“邮箱通知”时，同时发送价格调整邮件。
 * 广播在通知线程池异步执行，不阻塞顾问的保存请求。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CardPriceNoticeService {

    /** 产品类型：2=次卡（1 为 VIP 卡） */
    private static final int PRODUCT_TYPE_QUOTA = 2;

    private final UserMapper userMapper;
    private final NotificationService notificationService;
    private final EmailNotificationService emailNotificationService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 广播卡类价格变更通知
     *
     * @param before  变更前的价格快照
     * @param updates 本次提交的价格变更项（价格未变的项不进入通知内容）
     */
    @Async("notificationExecutor")
    public void notifyPriceChanged(List<VipPricing> before, List<VipPricingService.PriceUpdate> updates) {
        String text = buildChangeText(before, updates);
        if (text == null) {
            log.info("[卡类价格] 价格未发生变化，跳过变更通知");
            return;
        }

        List<User> users = userMapper.selectAll();
        int notified = 0;
        for (User user : users) {
            if (user == null || user.getId() == null) {
                continue;
            }
            Integer userId = user.getId().intValue();
            try {
                // 1. 系统通知：随“系统公告”开关，默认开启
                if (isSystemNoticeEnabled(userId)) {
                    notificationService.createAndPushNotification(userId, text,
                            NotificationConstant.TYPE_SYSTEM, null);
                    notified++;
                }
                // 2. 邮件通知：开关判断在 EmailNotificationService 内部，仅勾选“邮箱通知”的用户会收到
                emailNotificationService.sendCardPriceChangedEmail(userId, text);
            } catch (Exception e) {
                log.error("[卡类价格] 变更通知发送失败, userId: {}", userId, e);
            }
        }
        log.info("[卡类价格] 变更通知广播完成，用户 {} 人，系统通知 {} 条，内容：{}",
                users.size(), notified, text.replace("\n", " "));
    }

    /**
     * 拼接用户可见的价格变更文案
     *
     * @return 变更文案；无有效变更时返回 null
     */
    private String buildChangeText(List<VipPricing> before, List<VipPricingService.PriceUpdate> updates) {
        if (before == null || updates == null || updates.isEmpty()) {
            return null;
        }
        Map<Long, VipPricing> beforeById = new HashMap<>();
        for (VipPricing pricing : before) {
            if (pricing != null && pricing.getId() != null) {
                beforeById.put(pricing.getId(), pricing);
            }
        }

        List<String> lines = new ArrayList<>();
        for (VipPricingService.PriceUpdate update : updates) {
            if (update == null || update.id() == null || update.priceYuan() == null) {
                continue;
            }
            VipPricing old = beforeById.get(update.id());
            if (old == null || old.getPriceYuan() == null
                    || old.getPriceYuan().compareTo(update.priceYuan()) == 0) {
                continue;
            }
            lines.add(priceLine(old, update.priceYuan()));
        }
        if (lines.isEmpty()) {
            return null;
        }
        return "【卡类价格调整】\n" + String.join("\n", lines) + "\n新价格已生效，以开卡页展示为准。";
    }

    /**
     * 单行变更文案：产品名：￥旧价 → ￥新价
     */
    private String priceLine(VipPricing old, BigDecimal newPriceYuan) {
        String name = old.getProductType() != null && old.getProductType() == PRODUCT_TYPE_QUOTA
                ? "次卡 · " + old.getProductName()
                : "VIP 卡 · " + old.getProductName();
        return name + "：￥" + amount(old.getPriceYuan()) + " → ￥" + amount(newPriceYuan);
    }

    /**
     * 金额统一按两位小数展示
     */
    private String amount(BigDecimal priceYuan) {
        return priceYuan.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /**
     * 用户是否接收系统公告（未设置时默认接收，与通知设置默认值一致）
     */
    private boolean isSystemNoticeEnabled(Integer userId) {
        NotificationSettingsVO settings = ServiceHelper.getFromCache(redisTemplate, objectMapper,
                UserConstant.USER_NOTIFICATION_SETTINGS + userId, NotificationSettingsVO.class);
        return settings == null || settings.getSystem() == null || Boolean.TRUE.equals(settings.getSystem());
    }
}