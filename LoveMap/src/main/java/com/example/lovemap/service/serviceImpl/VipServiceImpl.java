package com.example.lovemap.service.serviceImpl;

import com.example.lovemap.common.PageResult;
import com.example.lovemap.common.Result;
import com.example.lovemap.common.ResultCode;
import com.example.lovemap.common.constant.NotificationConstant;
import com.example.lovemap.common.constant.QuotaProductMapping;
import com.example.lovemap.common.constant.QuotaType;
import com.example.lovemap.common.constant.UserConstant;
import com.example.lovemap.common.constant.VipConstant;
import com.example.lovemap.mapper.UserMapper;
import com.example.lovemap.mapper.VipOrderMapper;
import com.example.lovemap.makeover.mapper.MakeoverQuotaGrantMapper;
import com.example.lovemap.model.dto.VipOrderDTO;
import com.example.lovemap.model.dto.VipOrderHistoryQueryDTO;
import com.example.lovemap.model.entity.User;
import com.example.lovemap.model.entity.MakeoverQuotaGrant;
import com.example.lovemap.model.entity.VipOrder;
import com.example.lovemap.model.vo.VipOrderHistoryVO;
import com.example.lovemap.model.vo.VipOrderVO;
import com.example.lovemap.model.vo.VipPendingOrderVO;
import com.example.lovemap.model.vo.VipTierVO;
import com.example.lovemap.service.AsyncMailService;
import com.example.lovemap.service.EmailNotificationService;
import com.example.lovemap.service.NotificationService;
import com.example.lovemap.service.QuotaService;
import com.example.lovemap.service.VipPricingService;
import com.example.lovemap.service.VipService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * VIP 会员服务实现
 * <p>
 * 下单只登记订单，不直接开通；顾问收款后调用 activateOrder 完成开通。
 * 价格从 vip_pricing 表读取，由 VipPricingService 提供。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VipServiceImpl implements VipService {

    /** 订单号时间部分格式 */
    private static final DateTimeFormatter ORDER_NO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    /** 开通通知文案中的时间格式 */
    private static final DateTimeFormatter VIP_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** 入账月份格式 */
    private static final DateTimeFormatter GRANT_PERIOD_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");

    private final UserMapper userMapper;
    private final VipOrderMapper vipOrderMapper;
    private final VipPricingService vipPricingService;
    private final StringRedisTemplate redisTemplate;
    private final AsyncMailService asyncMailService;
    private final NotificationService notificationService;
    private final EmailNotificationService emailNotificationService;
    private final QuotaService quotaService;
    private final MakeoverQuotaGrantMapper makeoverQuotaGrantMapper;

    /** 顾问开通接口密钥，留空表示关闭该接口 */
    @Value("${vip.admin-token:}")
    private String adminToken;

    @Value("${vip.advisor-email:xjl20041115@126.com}")
    private String advisorEmail;

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
    public Result<PageResult<VipOrderHistoryVO>> listOrdersHistory(VipOrderHistoryQueryDTO query) {
        if (query == null) {
            query = new VipOrderHistoryQueryDTO();
        }
        int page = query.getPage() == null ? 1 : query.getPage();
        int size = query.getSize() == null ? 20 : query.getSize();

        long total = vipOrderMapper.countOrderHistory(query);
        List<VipOrderHistoryVO> list = total == 0 ? List.of() : vipOrderMapper.selectOrderHistory(query);
        // 填充档位名称与状态名称
        list.forEach(vo -> {
            vo.setVipLevelName(VipConstant.levelName(vo.getVipLevel()));
            vo.setStatusName(statusName(vo.getStatus()));
        });
        return Result.success(new PageResult<>(list, total, page, size));
    }

    /** 订单状态名称映射 */
    private static String statusName(Integer status) {
        if (status == null) {
            return "-";
        }
        return switch (status) {
            case VipConstant.ORDER_STATUS_PENDING -> "待开通";
            case VipConstant.ORDER_STATUS_ACTIVATED -> "已开通";
            case VipConstant.ORDER_STATUS_CANCELED -> "已取消";
            default -> "未知";
        };
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Object> createOrder(Integer userId, VipOrderDTO dto) {
        // orderType 默认为 VIP 卡
        int orderType = dto.getOrderType() == null ? VipConstant.ORDER_TYPE_VIP : dto.getOrderType();
        if (orderType == VipConstant.ORDER_TYPE_VIP) {
            return createVipOrder(userId, dto);
        }
        if (orderType == VipConstant.ORDER_TYPE_QUOTA) {
            return createQuotaOrder(userId, dto);
        }
        return Result.badRequest("订单类型不合法");
    }

    /**
     * VIP 卡下单流程
     */
    private Result<Object> createVipOrder(Integer userId, VipOrderDTO dto) {
        VipConstant.Tier tier = VipConstant.findTier(dto.getLevel());
        if (tier == null) {
            return Result.badRequest("VIP 档位不合法");
        }
        BigDecimal priceYuan = vipPricingService.getVipTierPrice(tier.level());
        if (priceYuan == null) {
            return Result.error(ResultCode.NOT_FOUND, "未配置该 VIP 档位价格，请联系管理员");
        }

        User user = userMapper.selectById(userId);
        if (user == null) {
            return Result.error(ResultCode.NOT_FOUND, "用户不存在");
        }
        if (resolveEffectiveVip(user).level() == VipConstant.LEVEL_FOREVER) {
            return Result.badRequest("您已是永久会员，无需重复下单");
        }

        // 充值后需能联系到用户，未绑定手机号时先引导去账户设置补全
        if (!StringUtils.hasText(user.getPhone())) {
            return Result.badRequest("请先在“账户设置”中绑定手机号，再进行 VIP 充值");
        }

        // 通用前置：检查同 groupId 待开通订单
        Result<Object> conflict = checkAndCancelPending(user, dto);
        if (conflict != null) {
            return conflict;
        }

        VipOrder order = new VipOrder();
        order.setOrderNo(generateOrderNo());
        order.setUserId(user.getId());
        order.setGroupId(user.getGroupId());
        order.setVipLevel(tier.level());
        order.setOrderType(VipConstant.ORDER_TYPE_VIP);
        order.setPriceYuan(priceYuan);
        order.setStatus(VipConstant.ORDER_STATUS_PENDING);
        order.setCreatedAt(LocalDateTime.now());
        vipOrderMapper.insert(order);

        log.info("[VIP] 用户 {} 下单 {}，订单号 {}，待顾问收款后开通", userId, tier.name(), order.getOrderNo());
        notifyAdvisor(user, tier, priceYuan, order);
        return Result.success(toOrderVO(order, tier, priceYuan));
    }

    /**
     * 次卡下单流程
     */
    private Result<Object> createQuotaOrder(Integer userId, VipOrderDTO dto) {
        Integer quotaType = dto.getQuotaType();
        Integer quotaCount = dto.getQuotaCount();
        if (quotaType == null || quotaCount == null) {
            return Result.badRequest("请选择次卡类型与次数");
        }
        String quotaTypeName = VipConstant.quotaProductName(quotaType);
        if ("未知".equals(quotaTypeName)) {
            return Result.badRequest("次卡类型不合法");
        }

        BigDecimal unitPrice = vipPricingService.getQuotaUnitPrice(quotaType);
        if (unitPrice == null) {
            return Result.badRequest("次卡类型不合法或未配置价格");
        }
        BigDecimal priceYuan = unitPrice.multiply(BigDecimal.valueOf(quotaCount))
                .setScale(2, RoundingMode.HALF_UP);

        User user = userMapper.selectById(userId);
        if (user == null) {
            return Result.error(ResultCode.NOT_FOUND, "用户不存在");
        }
        // 充值后需能联系到用户
        if (!StringUtils.hasText(user.getPhone())) {
            return Result.badRequest("请先在“账户设置”中绑定手机号，再购买次卡");
        }

        Result<Object> conflict = checkAndCancelPending(user, dto);
        if (conflict != null) {
            return conflict;
        }

        VipOrder order = new VipOrder();
        order.setOrderNo(generateOrderNo());
        order.setUserId(user.getId());
        order.setGroupId(user.getGroupId());
        // 次卡订单不占用 vip_level，使用 LEVEL_NORMAL 作占位
        order.setVipLevel(VipConstant.LEVEL_NORMAL);
        order.setOrderType(VipConstant.ORDER_TYPE_QUOTA);
        order.setQuotaType(quotaType);
        order.setQuotaCount(quotaCount);
        order.setPriceYuan(priceYuan);
        order.setStatus(VipConstant.ORDER_STATUS_PENDING);
        order.setCreatedAt(LocalDateTime.now());
        vipOrderMapper.insert(order);

        log.info("[VIP] 用户 {} 下单次卡 {} x{}，订单号 {}，待顾问收款后开通",
                userId, quotaTypeName, quotaCount, order.getOrderNo());
        notifyQuotaAdvisor(user, quotaTypeName, quotaCount, priceYuan, order);
        return Result.success(toQuotaOrderVO(order));
    }

    /**
     * 同本人或同 groupId 下若存在待开通订单，先告知用户；用户确认后才取消旧订单。
     * 无冲突时直接取消旧订单（避免顾问侧重复队列）并返回 null。
     */
    private Result<Object> checkAndCancelPending(User user, VipOrderDTO dto) {
        List<VipOrder> pendingList = vipOrderMapper.selectPendingOrdersByGroup(user.getId(), user.getGroupId(),
                VipConstant.ORDER_STATUS_PENDING);
        boolean hasPending = pendingList != null && !pendingList.isEmpty();
        boolean confirmed = Boolean.TRUE.equals(dto.getConfirmOldOrder());
        if (hasPending && !confirmed) {
            VipOrder old = pendingList.get(0);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("orderNo", old.getOrderNo());
            payload.put("orderType", old.getOrderType());
            payload.put("vipLevel", old.getVipLevel());
            payload.put("vipLevelName", VipConstant.levelName(old.getVipLevel()));
            payload.put("quotaType", old.getQuotaType());
            payload.put("quotaTypeName", VipConstant.quotaProductName(old.getQuotaType()));
            payload.put("quotaCount", old.getQuotaCount());
            payload.put("priceYuan", old.getPriceYuan());
            payload.put("createdAt", old.getCreatedAt());
            String msg = "您或您的伴侣存在尚未开通的订单，若继续下单，旧订单将自动取消";
            return Result.error(ResultCode.CONFLICT.getCode(), msg, payload);
        }
        vipOrderMapper.cancelPendingOrders(user.getId(), user.getGroupId(),
                VipConstant.ORDER_STATUS_PENDING, VipConstant.ORDER_STATUS_CANCELED);
        return null;
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

        // 按订单类型分支：VIP 卡 → 升级会员；次卡 → 入账配额
        Integer orderType = order.getOrderType();
        if (orderType == null || orderType == VipConstant.ORDER_TYPE_VIP) {
            return activateVipOrder(order);
        }
        if (orderType == VipConstant.ORDER_TYPE_QUOTA) {
            return activateQuotaOrder(order);
        }
        return Result.badRequest("订单类型不合法，无法开通");
    }

    /**
     * VIP 卡订单开通：升级用户等级，按情侣组同步
     */
    private Result<VipOrderVO> activateVipOrder(VipOrder order) {
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
                    order.getOrderNo(), partner.getId(), newLevel, expireAt);
        }

        vipOrderMapper.updateStatus(order.getId(), VipConstant.ORDER_STATUS_ACTIVATED, activatedAt);
        evictUserInfoCache(user);

        log.info("[VIP] 订单 {} 已开通，用户 {} 等级 {} -> {}，到期时间 {}",
                order.getOrderNo(), user.getId(), current.level(), newLevel, expireAt);

        notifyActivation(user, tier, order, activatedAt, expireAt);

        BigDecimal priceYuan = order.getPriceYuan();
        VipOrderVO vo = toOrderVO(order, tier, priceYuan);
        vo.setStatus(VipConstant.ORDER_STATUS_ACTIVATED);
        vo.setVipExpireAt(expireAt);
        return Result.success(vo);
    }

    /**
     * 次卡订单开通：订单置为已开通，入账次数由本周期已开通次卡订单累加进配额上限
     */
    private Result<VipOrderVO> activateQuotaOrder(VipOrder order) {
        Integer quotaType = order.getQuotaType();
        Integer quotaCount = order.getQuotaCount();
        if (quotaType == null || quotaCount == null || quotaCount <= 0) {
            return Result.badRequest("次卡订单参数异常，无法开通");
        }
        // 化妆建议走独立入账表，不写 quota_log，避免污染导出配额语义
        if (QuotaProductMapping.isMakeoverProduct(quotaType)) {
            return activateMakeoverQuotaOrder(order);
        }
        if (QuotaProductMapping.toQuotaType(quotaType) == -1) {
            return Result.badRequest("该次卡类型暂不支持自动开通，请联系管理员");
        }

        User user = userMapper.selectById(order.getUserId().intValue());
        if (user == null) {
            return Result.error(ResultCode.NOT_FOUND, "下单用户不存在");
        }

        LocalDateTime activatedAt = LocalDateTime.now();
        // 入账凭证就是订单本身（status=1 + activated_at + quota_count）：
        // quota_log 是使用流水，入账写进去会被算成已用，导致次数不增反减
        vipOrderMapper.updateStatus(order.getId(), VipConstant.ORDER_STATUS_ACTIVATED, activatedAt);

        String productName = VipConstant.quotaProductName(quotaType);
        log.info("[VIP] 次卡订单 {} 已开通，用户 {} 获得 {} x{} 次",
                order.getOrderNo(), user.getId(), productName, quotaCount);

        notifyQuotaActivation(user, productName, quotaCount, order);

        VipOrderVO vo = toQuotaOrderVO(order);
        vo.setStatus(VipConstant.ORDER_STATUS_ACTIVATED);
        return Result.success(vo);
    }
    /**
     * 化妆建议次卡开通：写入 makeover_quota_grant。
     * <p>
     * 不写 quota_log：化妆建议按月额度（VIP 档位 + 月免费 + 当月入账）在
     * makeover_record 里统计已用，与 quota_log 的导出/约会/心愿口径不一致，
     * 强行共用会污染导出配额语义。
     * <p>
     * 幂等：orderNo 唯一键兜底重复开通。
     */
    private Result<VipOrderVO> activateMakeoverQuotaOrder(VipOrder order) {
        User user = userMapper.selectById(order.getUserId().intValue());
        if (user == null) {
            return Result.error(ResultCode.NOT_FOUND, "下单用户不存在");
        }

        if (makeoverQuotaGrantMapper.selectByOrderNo(order.getOrderNo()) != null) {
            return Result.badRequest("该订单已入账，请勿重复开通");
        }

        LocalDateTime activatedAt = LocalDateTime.now();
        String period = activatedAt.format(GRANT_PERIOD_FORMATTER);
        LocalDateTime expiresAt = YearMonth.parse(period, GRANT_PERIOD_FORMATTER)
                .atEndOfMonth().atTime(LocalTime.MAX);

        MakeoverQuotaGrant grant = new MakeoverQuotaGrant();
        grant.setUserId(order.getUserId());
        grant.setOrderNo(order.getOrderNo());
        grant.setGrantCount(order.getQuotaCount());
        grant.setGrantPeriod(period);
        grant.setActivatedAt(activatedAt);
        grant.setExpiresAt(expiresAt);
        makeoverQuotaGrantMapper.insert(grant);

        vipOrderMapper.updateStatus(order.getId(), VipConstant.ORDER_STATUS_ACTIVATED, activatedAt);

        String productName = VipConstant.quotaProductName(order.getQuotaType());
        log.info("[VIP] 化妆次卡订单 {} 已开通，用户 {} 入账 {} 次（归属 {}）",
                order.getOrderNo(), user.getId(), order.getQuotaCount(), period);

        notifyQuotaActivation(user, productName, order.getQuotaCount(), order);

        VipOrderVO vo = toQuotaOrderVO(order);
        vo.setStatus(VipConstant.ORDER_STATUS_ACTIVATED);
        return Result.success(vo);
    }

    @Override
    public Result<List<VipOrderVO>> listOrders(Integer userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return Result.error(ResultCode.NOT_FOUND, "用户不存在");
        }
        List<VipOrder> orders = vipOrderMapper.selectByUserOrGroup(user.getId(), user.getGroupId());
        return Result.success(orders.stream().map(this::toOrderVO).toList());
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
    public com.example.lovemap.model.vo.VipQuotaVO currentQuotas(Integer userId) {
        User user = userMapper.selectById(userId);
        // 未登录用户/查不到用户时按普通用户处理，避免 NPE
        com.example.lovemap.model.vo.VipQuotaVO vo = new com.example.lovemap.model.vo.VipQuotaVO();
        vo.setAiRecommend(buildQuotaItem(user, QuotaType.AI_RECOMMEND));
        vo.setAiWishlist(buildQuotaItem(user, QuotaType.AI_WISHLIST));
        vo.setExport(buildQuotaItem(user, QuotaType.EXPORT));
        return vo;
    }

    private com.example.lovemap.model.vo.VipQuotaVO.QuotaItem buildQuotaItem(User user, int quotaType) {
        com.example.lovemap.model.vo.VipQuotaVO.QuotaItem item =
                new com.example.lovemap.model.vo.VipQuotaVO.QuotaItem();
        if (user == null) {
            item.setUsed(0);
            item.setLimit(0);
            item.setRemaining(0);
            item.setUnlimited(false);
            return item;
        }
        int limit = quotaService.limit(user, quotaType);
        int used = limit == -1 ? 0 : (limit - quotaService.remaining(user, quotaType));
        item.setLimit(limit);
        item.setUsed(used);
        item.setUnlimited(limit == -1);
        item.setRemaining(limit == -1 ? -1 : quotaService.remaining(user, quotaType));
        return item;
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
     * 收件邮箱来源于配置 vip.advisor-email（环境变量 VIP_ADVISOR_EMAIL）。
     */
    private void notifyAdvisor(User user, VipConstant.Tier tier, BigDecimal priceYuan, VipOrder order) {
        try {
            String nickname = StringUtils.hasText(user.getNickname()) ? user.getNickname() : "用户" + user.getId();
            asyncMailService.sendVipOrderMailAsync(advisorEmail, nickname, user.getPhone(), user.getEmail(),
                    tier.name(), priceYuan, order.getOrderNo());
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
     * 次卡开通后通知用户：系统通知 + 邮件
     */
    private void notifyQuotaActivation(User user, String productName, int count, VipOrder order) {
        String text = "次卡已开通：" + productName + " +" + count + " 次，订单号 " + order.getOrderNo();
        try {
            notificationService.createAndPushNotification(user.getId().intValue(), text,
                    NotificationConstant.TYPE_SYSTEM, order.getId());
        } catch (Exception e) {
            log.error("[VIP] 次卡 {} 开通通知发送失败, userId {}", order.getOrderNo(), user.getId(), e);
        }
    }

    /**
     * 次卡下单后通知顾问：邮件模板与 VIP 卡相同流程，由顾问收款后开通
     */
    private void notifyQuotaAdvisor(User user, String productName, int count, BigDecimal priceYuan, VipOrder order) {
        try {
            String nickname = StringUtils.hasText(user.getNickname()) ? user.getNickname() : "用户" + user.getId();
            asyncMailService.sendQuotaOrderMailAsync(advisorEmail, nickname, user.getPhone(), user.getEmail(),
                    productName, count, priceYuan, order.getOrderNo());
        } catch (Exception e) {
            log.error("[VIP] 次卡订单通知顾问失败, 订单号 {}", order.getOrderNo(), e);
        }
    }

    /** 次卡订单 VO（不展示 vipLevel/vipExpireAt 等 VIP 卡专属字段） */
    private VipOrderVO toQuotaOrderVO(VipOrder order) {
        VipOrderVO vo = new VipOrderVO();
        vo.setOrderNo(order.getOrderNo());
        vo.setOrderType(order.getOrderType());
        vo.setQuotaType(order.getQuotaType());
        vo.setQuotaTypeName(VipConstant.quotaProductName(order.getQuotaType()));
        vo.setQuotaCount(order.getQuotaCount());
        vo.setPriceYuan(order.getPriceYuan());
        vo.setStatus(order.getStatus());
        vo.setAdvisorEmail(advisorEmail);
        vo.setCreatedAt(order.getCreatedAt());
        vo.setActivatedAt(order.getActivatedAt());
        return vo;
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

    private VipOrderVO toOrderVO(VipOrder order, VipConstant.Tier tier, BigDecimal priceYuan) {
        VipOrderVO vo = new VipOrderVO();
        vo.setOrderNo(order.getOrderNo());
        vo.setVipLevel(order.getVipLevel());
        vo.setVipLevelName(tier == null ? null : tier.name());
        vo.setOrderType(order.getOrderType());
        vo.setQuotaType(order.getQuotaType());
        vo.setQuotaTypeName(VipConstant.quotaProductName(order.getQuotaType()));
        vo.setQuotaCount(order.getQuotaCount());
        vo.setPriceYuan(priceYuan);
        vo.setStatus(order.getStatus());
        vo.setAdvisorEmail(advisorEmail);
        vo.setCreatedAt(order.getCreatedAt());
        vo.setActivatedAt(order.getActivatedAt());
        return vo;
    }

    /** 账单列表：档位名称按订单里的 vipLevel 反查 */
    private VipOrderVO toOrderVO(VipOrder order) {
        if (order.getOrderType() != null && order.getOrderType() == VipConstant.ORDER_TYPE_QUOTA) {
            return toQuotaOrderVO(order);
        }
        return toOrderVO(order, VipConstant.findTier(order.getVipLevel()), order.getPriceYuan());
    }

    private VipTierVO toVO(VipConstant.Tier tier) {
        VipTierVO vo = new VipTierVO();
        vo.setLevel(tier.level());
        vo.setName(tier.name());
        // 价格从 vip_pricing 取，未配置时回退到 0 元并记日志（不至于直接抛错阻塞前端）
        BigDecimal priceYuan = vipPricingService.getVipTierPrice(tier.level());
        vo.setPriceYuan(priceYuan == null ? BigDecimal.ZERO : priceYuan);
        vo.setDurationText(tier.permanent() ? "永久" : tier.durationDays() + "天");
        vo.setPermanent(tier.permanent());
        // 化妆建议额度随配置变化，运行时拼接后追加到权益列表
        List<String> benefits = new ArrayList<>(tier.benefits());
        benefits.add(VipConstant.makeoverQuotaText(tier.level(), makeoverMonthlyFreeQuota));
        vo.setBenefits(benefits);
        return vo;
    }
}
