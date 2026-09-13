package com.example.lovemap.service.serviceImpl;

import com.example.lovemap.common.Result;
import com.example.lovemap.common.ResultCode;
import com.example.lovemap.common.constant.NotificationConstant;
import com.example.lovemap.common.constant.UserConstant;
import com.example.lovemap.common.constant.VipConstant;
import com.example.lovemap.mapper.UserMapper;
import com.example.lovemap.mapper.VipOrderMapper;
import com.example.lovemap.model.dto.VipOrderDTO;
import com.example.lovemap.model.entity.User;
import com.example.lovemap.model.entity.VipOrder;
import com.example.lovemap.model.vo.VipOrderVO;
import com.example.lovemap.model.vo.VipPendingOrderVO;
import com.example.lovemap.model.vo.VipTierVO;
import com.example.lovemap.service.AsyncMailService;
import com.example.lovemap.service.EmailNotificationService;
import com.example.lovemap.service.NotificationService;
import com.example.lovemap.service.VipService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * VIP 会员服务实现
 * <p>
 * 下单只登记订单，不直接开通；顾问收款后调用 activateOrder 完成开通。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VipServiceImpl implements VipService {

    /** 订单号时间部分格式 */
    private static final DateTimeFormatter ORDER_NO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    /** 开通通知文案中的时间格式 */
    private static final DateTimeFormatter VIP_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final UserMapper userMapper;
    private final VipOrderMapper vipOrderMapper;
    private final StringRedisTemplate redisTemplate;
    private final AsyncMailService asyncMailService;
    private final NotificationService notificationService;
    private final EmailNotificationService emailNotificationService;

    /** 顾问开通接口密钥，留空表示关闭该接口 */
    @Value("${vip.admin-token:}")
    private String adminToken;

    /** 对外访问地址（末尾不带 /），用于生成通知邮件里的开通命令 */
    @Value("${vip.public-base-url:http://localhost:8080}")
    private String publicBaseUrl;

    /** 每个账户每月免费 AI 化妆建议次数，用于在权益文案里展示档位额度 */
    @Value("${makeover.monthly-free-quota:3}")
    private int makeoverMonthlyFreeQuota;

    @Override
    public Result<List<VipTierVO>> listTiers() {
        return Result.success(VipConstant.TIERS.stream().map(this::toVO).toList());
    }

    @Override
    public Result<List<VipPendingOrderVO>> listPendingOrders() {
        List<VipPendingOrderVO> orders = vipOrderMapper.selectPendingOrders(VipConstant.ORDER_STATUS_PENDING);
        orders.forEach(order -> order.setVipLevelName(VipConstant.levelName(order.getVipLevel())));
        return Result.success(orders);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<VipOrderVO> createOrder(Integer userId, VipOrderDTO dto) {
        VipConstant.Tier tier = VipConstant.findTier(dto.getLevel());
        if (tier == null) {
            return Result.badRequest("VIP 档位不合法");
        }

        User user = userMapper.selectById(userId);
        if (user == null) {
            return Result.error(ResultCode.NOT_FOUND, "用户不存在");
        }
        if (resolveEffectiveVip(user).level() == VipConstant.LEVEL_FOREVER) {
            return Result.badRequest("您已是永久会员，无需重复下单");
        }

        // 一人同时只保留一张待开通订单，避免顾问侧出现重复队列
        vipOrderMapper.cancelPendingOrders(user.getId(),
                VipConstant.ORDER_STATUS_PENDING, VipConstant.ORDER_STATUS_CANCELED);

        VipOrder order = new VipOrder();
        order.setOrderNo(generateOrderNo());
        order.setUserId(user.getId());
        order.setGroupId(user.getGroupId());
        order.setVipLevel(tier.level());
        order.setPriceYuan(tier.priceYuan());
        order.setStatus(VipConstant.ORDER_STATUS_PENDING);
        order.setCreatedAt(LocalDateTime.now());
        vipOrderMapper.insert(order);

        log.info("[VIP] 用户 {} 下单 {}，订单号 {}，待顾问收款后开通", userId, tier.name(), order.getOrderNo());
        notifyAdvisor(user, tier, order);
        return Result.success(toOrderVO(order, tier));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<VipOrderVO> activateOrder(String orderNo) {
        VipOrder order = vipOrderMapper.selectByOrderNo(orderNo);
        if (order == null) {
            return Result.notFound("订单不存在");
        }
        if (order.getStatus() == VipConstant.ORDER_STATUS_ACTIVATED) {
            return Result.badRequest("该订单已开通，请勿重复操作");
        }
        if (order.getStatus() == VipConstant.ORDER_STATUS_CANCELED) {
            return Result.badRequest("该订单已取消，无法开通");
        }

        VipConstant.Tier tier = VipConstant.findTier(order.getVipLevel());
        if (tier == null) {
            return Result.badRequest("订单档位异常，无法开通");
        }

        User user = userMapper.selectById(order.getUserId().intValue());
        if (user == null) {
            return Result.error(ResultCode.NOT_FOUND, "下单用户不存在");
        }

        EffectiveVip current = resolveEffectiveVip(user);
        if (current.level() == VipConstant.LEVEL_FOREVER) {
            return Result.badRequest("该用户已是永久会员，请取消订单并联系用户退款");
        }

        // 到期时间以开通时间为基准，顾问处理耗时不计入用户时长；仍在会员期内则顺延，避免吞掉剩余天数
        LocalDateTime activatedAt = LocalDateTime.now();
        LocalDateTime base = (current.expireAt() != null && current.expireAt().isAfter(activatedAt))
                ? current.expireAt() : activatedAt;
        LocalDateTime expireAt = tier.permanent() ? null : base.plusDays(tier.durationDays());
        int newLevel = Math.max(current.level(), tier.level());

        userMapper.updateVip(user.getId(), newLevel, expireAt);

        // VIP 归属情侣组：已绑定伴侣时同步写入，保证双方 vip_level / vip_expire_at 一致
        User partner = user.getPartnerId() == null
                ? null : userMapper.selectById(user.getPartnerId().intValue());
        if (partner != null) {
            userMapper.updateVip(partner.getId(), newLevel, expireAt);
            log.info("[VIP] 订单 {} 已同步开通伴侣 {} 的 VIP，等级 {}，到期时间 {}",
                    orderNo, partner.getId(), newLevel, expireAt);
        }

        vipOrderMapper.updateStatus(order.getId(), VipConstant.ORDER_STATUS_ACTIVATED, activatedAt);
        evictUserInfoCache(user);

        log.info("[VIP] 订单 {} 已开通，用户 {} 等级 {} -> {}，到期时间 {}",
                orderNo, user.getId(), current.level(), newLevel, expireAt);

        notifyActivation(user, tier, order, activatedAt, expireAt);

        VipOrderVO vo = toOrderVO(order, tier);
        vo.setStatus(VipConstant.ORDER_STATUS_ACTIVATED);
        vo.setVipExpireAt(expireAt);
        return Result.success(vo);
    }

    @Override
    public Result<List<VipOrderVO>> listOrders(Integer userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return Result.error(ResultCode.NOT_FOUND, "用户不存在");
        }
        List<VipOrder> orders = vipOrderMapper.selectByUserOrGroup(user.getId(), user.getGroupId());
        return Result.success(orders.stream().map(VipServiceImpl::toOrderVO).toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<VipOrderVO> cancelOrder(Integer userId, String orderNo) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return Result.error(ResultCode.NOT_FOUND, "用户不存在");
        }

        VipOrder order = vipOrderMapper.selectByOrderNo(orderNo);
        if (order == null) {
            return Result.notFound("订单不存在");
        }
        if (!belongsToUser(user, order)) {
            return Result.forbidden("无权操作该订单");
        }
        if (order.getStatus() == VipConstant.ORDER_STATUS_ACTIVATED) {
            return Result.badRequest("该订单已开通，无法取消");
        }
        if (order.getStatus() == VipConstant.ORDER_STATUS_CANCELED) {
            return Result.badRequest("该订单已取消，请勿重复操作");
        }

        // 条件更新：订单若已被顾问开通，影响行数为 0，避免把已开通订单改成已取消
        int rows = vipOrderMapper.cancelPendingOrder(order.getId(),
                VipConstant.ORDER_STATUS_PENDING, VipConstant.ORDER_STATUS_CANCELED);
        if (rows == 0) {
            return Result.badRequest("订单状态已变更，请刷新后重试");
        }

        log.info("[VIP] 用户 {} 取消订单 {}", userId, orderNo);
        order.setStatus(VipConstant.ORDER_STATUS_CANCELED);
        return Result.success(toOrderVO(order));
    }

    @Override
    public boolean isValidAdminToken(String token) {
        if (!StringUtils.hasText(adminToken) || !StringUtils.hasText(token)) {
            return false;
        }
        return MessageDigest.isEqual(adminToken.getBytes(StandardCharsets.UTF_8),
                token.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public EffectiveVip resolveEffectiveVip(User user) {
        if (user == null) {
            return normalVip();
        }

        User best = user;
        int bestLevel = effectiveLevel(user);
        if (user.getGroupId() != null) {
            for (User member : userMapper.selectByGroupId(user.getGroupId())) {
                int level = effectiveLevel(member);
                boolean better = level > bestLevel
                        || (level == bestLevel && level > VipConstant.LEVEL_NORMAL
                        && isLater(member.getVipExpireAt(), best.getVipExpireAt()));
                if (better) {
                    best = member;
                    bestLevel = level;
                }
            }
        }

        if (bestLevel == VipConstant.LEVEL_NORMAL) {
            return normalVip();
        }
        return new EffectiveVip(bestLevel, VipConstant.levelName(bestLevel), best.getVipExpireAt());
    }

    /**
     * 单个用户的生效等级：等级非法或已过期均按普通用户计算，永久卡不看到期时间
     */
    private int effectiveLevel(User user) {
        Integer raw = user.getVipLevel();
        if (raw == null || VipConstant.findTier(raw) == null) {
            return VipConstant.LEVEL_NORMAL;
        }
        if (raw == VipConstant.LEVEL_FOREVER) {
            return VipConstant.LEVEL_FOREVER;
        }
        LocalDateTime expireAt = user.getVipExpireAt();
        if (expireAt == null || !expireAt.isAfter(LocalDateTime.now())) {
            return VipConstant.LEVEL_NORMAL;
        }
        return raw;
    }

    private boolean isLater(LocalDateTime candidate, LocalDateTime current) {
        if (candidate == null) {
            return false;
        }
        return current == null || candidate.isAfter(current);
    }

    /** 订单归属：本人下单，或与本人属于同一情侣组（账单按组共享） */
    private boolean belongsToUser(User user, VipOrder order) {
        if (order.getUserId() != null && order.getUserId().equals(user.getId())) {
            return true;
        }
        return user.getGroupId() != null && user.getGroupId().equals(order.getGroupId());
    }

    /**
     * 邮件通知专属顾问有待收款开通的订单
     * <p>
     * 邮件发送失败只记日志，不影响用户下单结果；顾问可凭订单号在库中补查。
     */
    private void notifyAdvisor(User user, VipConstant.Tier tier, VipOrder order) {
        try {
            String token = StringUtils.hasText(adminToken) ? adminToken : "<未配置 VIP_ADMIN_TOKEN>";
            String activateCommand = "curl -X POST \"" + publicBaseUrl + "/api/vip/admin/orders/"
                    + order.getOrderNo() + "/activate\" -H \"X-Vip-Admin-Token: " + token + "\"";
            String nickname = StringUtils.hasText(user.getNickname()) ? user.getNickname() : "用户" + user.getId();
            asyncMailService.sendVipOrderMailAsync(VipConstant.ADVISOR_EMAIL, nickname, tier.name(),
                    tier.priceYuan(), order.getOrderNo(), activateCommand);
        } catch (Exception e) {
            log.error("[VIP] 通知顾问失败, 订单号 {}", order.getOrderNo(), e);
        }
    }

    /**
     * 开通成功后通知情侣组双方：系统通知 + 邮箱提醒
     * <p>
     * VIP 归属情侣组，下单用户与绑定伴侣都要收到；任一收件人发送失败只记日志，不影响开通结果。
     */
    private void notifyActivation(User payer, VipConstant.Tier tier, VipOrder order,
                                  LocalDateTime activatedAt, LocalDateTime expireAt) {
        String activatedAtText = activatedAt.format(VIP_TIME_FORMATTER);
        String expireAtText = expireAt == null ? "永久有效" : expireAt.format(VIP_TIME_FORMATTER);
        String payerName = StringUtils.hasText(payer.getNickname()) ? payer.getNickname() : "用户" + payer.getId();

        List<User> members = payer.getGroupId() != null
                ? userMapper.selectByGroupId(payer.getGroupId())
                : List.of(payer);
        for (User member : members) {
            if (member == null || member.getId() == null) {
                continue;
            }
            try {
                String text = member.getId().equals(payer.getId())
                        ? "VIP 已开通：" + tier.name() + "，开通时间 " + activatedAtText + "，到期时间 " + expireAtText
                        : "您的伴侣 " + payerName + " 已开通 VIP：" + tier.name() + "，双方共享权益，开通时间 "
                                + activatedAtText + "，到期时间 " + expireAtText;
                notificationService.createAndPushNotification(member.getId().intValue(), text,
                        NotificationConstant.TYPE_SYSTEM, order.getId());
                emailNotificationService.sendVipActivatedEmail(member.getId().intValue(), tier.name(),
                        activatedAtText, expireAtText);
            } catch (Exception e) {
                log.error("[VIP] 订单 {} 开通通知发送失败, userId {}", order.getOrderNo(), member.getId(), e);
            }
        }
    }

    /**
     * 清除用户信息缓存：UserVO 携带 VIP 字段，且 VIP 归属情侣组，需同时清除双方缓存
     */
    private void evictUserInfoCache(User user) {
        redisTemplate.delete(UserConstant.USER_INFO + user.getId());
        if (user.getGroupId() != null) {
            for (User member : userMapper.selectByGroupId(user.getGroupId())) {
                redisTemplate.delete(UserConstant.USER_INFO + member.getId());
            }
        }
    }

    private EffectiveVip normalVip() {
        return new EffectiveVip(VipConstant.LEVEL_NORMAL, VipConstant.LEVEL_NORMAL_NAME, null);
    }

    /** 订单号：VIP + 毫秒时间戳 + 3 位随机数 */
    private static String generateOrderNo() {
        return "VIP" + LocalDateTime.now().format(ORDER_NO_FORMATTER)
                + String.format("%03d", ThreadLocalRandom.current().nextInt(1000));
    }

    private static VipOrderVO toOrderVO(VipOrder order, VipConstant.Tier tier) {
        VipOrderVO vo = new VipOrderVO();
        vo.setOrderNo(order.getOrderNo());
        vo.setVipLevel(order.getVipLevel());
        vo.setVipLevelName(tier.name());
        vo.setPriceYuan(order.getPriceYuan());
        vo.setStatus(order.getStatus());
        vo.setAdvisorEmail(VipConstant.ADVISOR_EMAIL);
        vo.setCreatedAt(order.getCreatedAt());
        vo.setActivatedAt(order.getActivatedAt());
        return vo;
    }

    /** 账单列表：档位名称按订单里的 vipLevel 反查 */
    private static VipOrderVO toOrderVO(VipOrder order) {
        return toOrderVO(order, VipConstant.findTier(order.getVipLevel()));
    }

    private VipTierVO toVO(VipConstant.Tier tier) {
        VipTierVO vo = new VipTierVO();
        vo.setLevel(tier.level());
        vo.setName(tier.name());
        vo.setPriceYuan(tier.priceYuan());
        vo.setDurationText(tier.permanent() ? "永久" : tier.durationDays() + "天");
        vo.setPermanent(tier.permanent());
        // 化妆建议额度随配置变化，运行时拼接后追加到权益列表
        List<String> benefits = new ArrayList<>(tier.benefits());
        benefits.add(VipConstant.makeoverQuotaText(tier.level(), makeoverMonthlyFreeQuota));
        vo.setBenefits(benefits);
        return vo;
    }
}
