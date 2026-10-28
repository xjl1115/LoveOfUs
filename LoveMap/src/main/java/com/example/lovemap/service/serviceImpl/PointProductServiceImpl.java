package com.example.lovemap.service.serviceImpl;

import com.example.lovemap.common.BusinessException;
import com.example.lovemap.common.Result;
import com.example.lovemap.common.ResultCode;
import com.example.lovemap.common.constant.NotificationConstant;
import com.example.lovemap.common.constant.PointsConstant;
import com.example.lovemap.mapper.PointExchangeMapper;
import com.example.lovemap.mapper.PointProductMapper;
import com.example.lovemap.mapper.ShippingAddressMapper;
import com.example.lovemap.mapper.UserMapper;
import com.example.lovemap.mapper.UserPointsLogMapper;
import com.example.lovemap.mapper.UserPointsMapper;
import com.example.lovemap.model.dto.PointExchangeDTO;
import com.example.lovemap.model.dto.PointProductSaveDTO;
import com.example.lovemap.model.entity.PointExchange;
import com.example.lovemap.model.entity.PointProduct;
import com.example.lovemap.model.entity.ShippingAddress;
import com.example.lovemap.model.entity.User;
import com.example.lovemap.model.entity.UserPoints;
import com.example.lovemap.model.entity.UserPointsLog;
import com.example.lovemap.model.vo.PointOrderVO;
import com.example.lovemap.model.vo.PointProductVO;
import com.example.lovemap.service.AsyncMailService;
import com.example.lovemap.service.EmailNotificationService;
import com.example.lovemap.service.NotificationService;
import com.example.lovemap.service.PointProductService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * 积分商品实现
 *
 * 价格口径：
 *   - 促销开启时，积分与金额均取促销值，否则取原值
 *   - 金额（effective amount）为 NULL 或 0 → 纯积分兑换，即时发放
 *   - 金额大于 0 → 生成待确认订单，顾问线下收款后在后台确认发放
 *
 * 缓存策略（与 VipPricingService 一致）：
 *   key  = point:product:list
 *   TTL  = 1 小时
 *   写操作（save / delete）主动失效
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PointProductServiceImpl implements PointProductService {

    /** 顾问控制台 + 前端用户共用同一份缓存（顾问修改后失效） */
    private static final String CACHE_KEY = "point:product:list";
    private static final Duration CACHE_TTL = Duration.ofHours(1);

    /** 订单号时间部分格式 */
    private static final DateTimeFormatter ORDER_NO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final PointProductMapper productMapper;
    private final PointExchangeMapper exchangeMapper;
    private final ShippingAddressMapper shippingAddressMapper;
    private final UserPointsMapper userPointsMapper;
    private final UserPointsLogMapper userPointsLogMapper;
    private final UserMapper userMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final AsyncMailService asyncMailService;
    private final NotificationService notificationService;
    private final EmailNotificationService emailNotificationService;

    /** 顾问收件邮箱（商品订单通知邮件的收件人） */
    @Value("${vip.advisor-email:xjl20041115@126.com}")
    private String advisorEmail;

    @Override
    public Result<List<PointProductVO>> listOnShelf() {
        return Result.success(toVOs(productMapper.selectOnShelf()));
    }

    @Override
    public Result<List<PointProductVO>> listAll() {
        List<PointProduct> all = readCache();
        if (all == null) {
            all = productMapper.selectAll();
            writeCache(all);
        }
        return Result.success(toVOs(all));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Long> save(PointProductSaveDTO dto) {
        int promoStatus = dto.getPromoStatus() == null ? PointsConstant.PROMO_OFF : dto.getPromoStatus();
        if (promoStatus == PointsConstant.PROMO_ON
                && (dto.getPromoPoints() == null || dto.getPromoPoints() <= 0)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "开启促销时必须设置促销积分");
        }

        PointProduct p = new PointProduct();
        p.setId(dto.getId());
        p.setName(dto.getName().trim());
        p.setDescription(dto.getDescription());
        p.setPointsCost(dto.getPointsCost());
        p.setPriceAmount(dto.getPriceAmount());
        p.setPromoStatus(promoStatus);
        p.setPromoPoints(promoStatus == PointsConstant.PROMO_ON ? dto.getPromoPoints() : null);
        p.setPromoAmount(promoStatus == PointsConstant.PROMO_ON ? dto.getPromoAmount() : null);
        p.setStock(dto.getStock());
        p.setImageUrl(dto.getImageUrl());
        p.setStatus(dto.getStatus());
        p.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : 0);

        if (dto.getId() == null) {
            productMapper.insert(p);
        } else {
            if (productMapper.selectById(dto.getId()) == null) {
                throw new BusinessException(ResultCode.NOT_FOUND, "商品不存在");
            }
            p.setId(dto.getId());
            productMapper.update(p);
        }
        evictCache();
        return Result.success(p.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> delete(Long id) {
        if (productMapper.selectById(id) == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "商品不存在");
        }
        productMapper.deleteById(id);
        evictCache();
        return Result.success();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<PointOrderVO> exchange(Integer userId, PointExchangeDTO dto) {
        User user = requireUser(userId);
        int qty = dto.getQuantity() == null ? 1 : dto.getQuantity();
        if (qty <= 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "兑换数量必须大于 0");
        }

        PointProduct product = productMapper.selectById(dto.getProductId());
        if (product == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "商品不存在");
        }
        if (product.getStatus() == null || product.getStatus() != PointsConstant.PRODUCT_ON_SHELF) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "商品已下架");
        }
        if (product.getStock() != -1 && product.getStock() < qty) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "库存不足");
        }

        // 促销生效时，积分与金额均按促销值计算
        boolean promoOn = product.getPromoStatus() != null
                && product.getPromoStatus() == PointsConstant.PROMO_ON;
        int unitPoints = (promoOn && product.getPromoPoints() != null)
                ? product.getPromoPoints() : product.getPointsCost();
        BigDecimal unitAmount = promoOn
                ? firstNotNull(product.getPromoAmount(), product.getPriceAmount())
                : product.getPriceAmount();

        int totalPoints = unitPoints * qty;
        boolean needPay = unitAmount != null && unitAmount.compareTo(BigDecimal.ZERO) > 0;

        // 确保积分账户存在
        if (userPointsMapper.selectByUserId(user.getId()) == null) {
            userPointsMapper.insertIgnore(user.getId());
        }

        // 所有商品兑换均需先选择收货地址（含纯积分商品）
        ShippingAddress address = resolveAddress(user.getId(), dto.getAddressId());

        if (!needPay) {
            return doImmediateExchange(user, product, qty, unitPoints, totalPoints, address);
        }
        return createPendingOrder(user, product, qty, unitPoints, unitAmount, totalPoints, address);
    }

    /**
     * 纯积分兑换：即时扣库存、扣积分、写流水与兑换记录
     */
    private Result<PointOrderVO> doImmediateExchange(User user, PointProduct product,
                                                     int qty, int unitPoints, int totalPoints,
                                                     ShippingAddress address) {
        // 1) 扣减库存（循环 qty 次；-1 不限库存跳过）
        if (product.getStock() != -1) {
            for (int i = 0; i < qty; i++) {
                int affected = productMapper.decrStock(product.getId());
                if (affected != 1) {
                    throw new BusinessException(ResultCode.BAD_REQUEST, "库存不足，兑换失败");
                }
            }
            // 库存已变化，失效商品列表缓存，保证顾问控制台读到最新库存
            evictCache();
        }

        // 2) 扣减积分（带余额条件）
        int affected = userPointsMapper.decrPoints(user.getId(), totalPoints);
        if (affected != 1) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "积分不足");
        }

        // 3) 写积分流水
        writePointsLog(user.getId(), -totalPoints, PointsConstant.REF_EXCHANGE, product.getId(),
                "兑换商品：" + product.getName() + " x" + qty);

        // 4) 写兑换记录（即时发放）
        PointExchange exchange = new PointExchange();
        exchange.setUserId(user.getId());
        exchange.setProductId(product.getId());
        exchange.setProductName(product.getName());
        exchange.setPointsCost(unitPoints);
        exchange.setQuantity(qty);
        exchange.setTotalPoints(totalPoints);
        exchange.setReceiverName(address.getReceiverName());
        exchange.setReceiverPhone(address.getReceiverPhone());
        exchange.setShippingAddress(address.getProvince() + address.getCity() + address.getDetailAddress());
        exchange.setStatus(PointsConstant.EXCHANGE_SUCCESS);
        exchange.setConfirmedAt(LocalDateTime.now());
        exchangeMapper.insert(exchange);

        log.info("[积分兑换] userId={} productId={} qty={} totalCost={}",
                user.getId(), product.getId(), qty, totalPoints);
        return Result.success("兑换成功", toOrderVO(exchange));
    }

    /**
     * 含金额商品下单：登记待确认订单并通知顾问，确认收款后才扣积分与库存
     */
    private Result<PointOrderVO> createPendingOrder(User user, PointProduct product, int qty,
                                                    int unitPoints, BigDecimal unitAmount, int totalPoints,
                                                    ShippingAddress address) {
        if (!StringUtils.hasText(user.getPhone())) {
            return Result.badRequest("请先在“账户设置”中绑定手机号，再进行兑换");
        }
        // 下单即校验积分，避免顾问确认时才发现余额不足
        UserPoints account = userPointsMapper.selectByUserId(user.getId());
        if (account == null || account.getPoints() < totalPoints) {
            return Result.badRequest("积分不足");
        }

        BigDecimal totalAmount = unitAmount.multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP);

        PointExchange order = new PointExchange();
        order.setOrderNo(generateOrderNo());
        order.setUserId(user.getId());
        order.setProductId(product.getId());
        order.setProductName(product.getName());
        order.setPointsCost(unitPoints);
        order.setQuantity(qty);
        order.setTotalPoints(totalPoints);
        order.setPriceAmount(totalAmount);
        order.setReceiverName(address.getReceiverName());
        order.setReceiverPhone(address.getReceiverPhone());
        order.setShippingAddress(address.getProvince() + address.getCity() + address.getDetailAddress());
        order.setStatus(PointsConstant.EXCHANGE_PENDING);
        exchangeMapper.insert(order);

        notifyAdvisor(user, product, order, totalAmount);
        log.info("[积分兑换] 用户 {} 下单商品 {} x{}，订单号 {}，待顾问确认收款",
                user.getId(), product.getName(), qty, order.getOrderNo());
        return Result.success("下单成功，请等待顾问确认收款", toOrderVO(order));
    }

    @Override
    public Result<List<PointExchange>> recentExchanges(Integer userId) {
        requireUser(userId);
        return Result.success(exchangeMapper.selectRecentByUser(userId.longValue()));
    }

    @Override
    public Result<List<PointExchange>> listPendingOrders() {
        return Result.success(exchangeMapper.selectPending());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<PointExchange> activateOrder(String orderNo) {
        PointExchange order = exchangeMapper.selectByOrderNo(orderNo);
        if (order == null) {
            return Result.notFound("订单不存在");
        }
        if (order.getStatus() == PointsConstant.EXCHANGE_SUCCESS) {
            return Result.badRequest("该订单已发放，请勿重复操作");
        }
        if (order.getStatus() == PointsConstant.EXCHANGE_CANCELED) {
            return Result.badRequest("该订单已取消，无法发放");
        }

        // 1) 扣减库存
        PointProduct product = productMapper.selectById(order.getProductId());
        if (product != null && product.getStock() != -1) {
            for (int i = 0; i < order.getQuantity(); i++) {
                if (productMapper.decrStock(product.getId()) != 1) {
                    throw new BusinessException(ResultCode.BAD_REQUEST, "库存不足，发放失败");
                }
            }
            // 库存已变化，失效商品列表缓存，保证顾问控制台读到最新库存
            evictCache();
        }

        // 2) 扣减积分（带余额条件）
        int affected = userPointsMapper.decrPoints(order.getUserId(), order.getTotalPoints());
        if (affected != 1) {
            return Result.badRequest("用户积分不足，无法发放，请与用户确认后取消订单");
        }
        writePointsLog(order.getUserId(), -order.getTotalPoints(), PointsConstant.REF_EXCHANGE,
                order.getProductId(), "兑换商品：" + order.getProductName()
                        + " x" + order.getQuantity() + "（订单 " + order.getOrderNo() + "）");

        // 3) 置为已发放
        int rows = exchangeMapper.updateStatus(order.getId(), PointsConstant.EXCHANGE_SUCCESS,
                LocalDateTime.now());
        if (rows == 0) {
            return Result.badRequest("订单状态已变更，请刷新后重试");
        }

        order.setStatus(PointsConstant.EXCHANGE_SUCCESS);
        order.setConfirmedAt(LocalDateTime.now());
        log.info("[积分兑换] 订单 {} 已确认发放，用户 {} 扣减 {} 积分",
                order.getOrderNo(), order.getUserId(), order.getTotalPoints());

        notifyExchangeSuccess(order);
        return Result.success("已确认发放", order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> cancelOrder(String orderNo) {
        PointExchange order = exchangeMapper.selectByOrderNo(orderNo);
        if (order == null) {
            return Result.notFound("订单不存在");
        }
        if (order.getStatus() != PointsConstant.EXCHANGE_PENDING) {
            return Result.badRequest("仅待确认订单可以取消");
        }
        exchangeMapper.updateStatus(order.getId(), PointsConstant.EXCHANGE_CANCELED, LocalDateTime.now());
        log.info("[积分兑换] 订单 {} 已取消", orderNo);
        return Result.success();
    }

    @Override
    public PointProductVO toVO(PointProduct p) {
        if (p == null) {
            return null;
        }
        PointProductVO vo = new PointProductVO();
        vo.setId(p.getId());
        vo.setName(p.getName());
        vo.setDescription(p.getDescription());
        vo.setPointsCost(p.getPointsCost());
        vo.setPriceAmount(p.getPriceAmount());
        vo.setPromoStatus(p.getPromoStatus());
        vo.setPromoPoints(p.getPromoPoints());
        vo.setPromoAmount(p.getPromoAmount());
        vo.setStock(p.getStock());
        vo.setInStock(p.getStock() == -1 || p.getStock() > 0);
        vo.setImageUrl(p.getImageUrl());
        vo.setStatus(p.getStatus());
        vo.setSortOrder(p.getSortOrder());
        vo.setUpdatedAt(p.getUpdatedAt());
        return vo;
    }

    // ==================== 私有 ====================

    /**
     * 下单结果 VO：附带顾问邮箱，供前端提示用户联系顾问完成线下付款
     */
    private PointOrderVO toOrderVO(PointExchange exchange) {
        PointOrderVO vo = new PointOrderVO();
        vo.setOrderNo(exchange.getOrderNo());
        vo.setProductName(exchange.getProductName());
        vo.setTotalPoints(exchange.getTotalPoints());
        vo.setPriceAmount(exchange.getPriceAmount());
        vo.setStatus(exchange.getStatus());
        vo.setAdvisorEmail(advisorEmail);
        return vo;
    }

    private User requireUser(Integer userId) {
        User u = userMapper.selectById(userId);
        if (u == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "用户不存在");
        }
        return u;
    }

    /** 校验并返回当前用户的收货地址（所有商品兑换时必须指定） */
    private ShippingAddress resolveAddress(Long userId, Long addressId) {
        if (addressId == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "请选择收货地址");
        }
        ShippingAddress address = shippingAddressMapper.selectById(addressId);
        if (address == null || address.getUserId() == null || !address.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "收货地址不存在");
        }
        return address;
    }

    private void writePointsLog(Long userId, int changePoints, String refType, Long refId, String remark) {
        UserPoints account = userPointsMapper.selectByUserId(userId);
        UserPointsLog logRow = new UserPointsLog();
        logRow.setUserId(userId);
        logRow.setChangeType(PointsConstant.CHANGE_TYPE_EXCHANGE);
        logRow.setChangePoints(changePoints);
        logRow.setBalanceAfter(account.getPoints());
        logRow.setRefType(refType);
        logRow.setRefId(refId);
        logRow.setRemark(remark);
        userPointsLogMapper.insert(logRow);
    }

    private void notifyAdvisor(User user, PointProduct product, PointExchange order, BigDecimal totalAmount) {
        try {
            asyncMailService.sendPointOrderMailAsync(advisorEmail, user.getNickname(), user.getPhone(),
                    user.getEmail(), product.getName(), order.getPointsCost(), order.getQuantity(),
                    totalAmount, order.getOrderNo(), order.getReceiverName(), order.getShippingAddress());
        } catch (Exception e) {
            // 通知失败不阻断下单，顾问可在后台待确认列表看到订单
            log.error("[积分兑换] 顾问通知邮件发送失败, orderNo={}", order.getOrderNo(), e);
        }
    }

    /**
     * 顾问确认发放后通知用户：系统通知（写库 + SSE 推送）+ 邮件（用户开启邮箱通知时发送）
     * <p>
     * 与 VIP 开通通知一致，通知失败不回滚订单状态，仅记录日志。
     */
    private void notifyExchangeSuccess(PointExchange order) {
        String amountText = order.getPriceAmount() == null
                ? "0" : order.getPriceAmount().stripTrailingZeros().toPlainString();
        String text = "兑换成功：商品「" + order.getProductName() + "」×" + order.getQuantity()
                + "，已扣除 " + order.getTotalPoints() + " 积分，付款 " + amountText
                + " 元，订单号 " + order.getOrderNo();
        try {
            notificationService.createAndPushNotification(order.getUserId().intValue(), text,
                    NotificationConstant.TYPE_SYSTEM, order.getId());
        } catch (Exception e) {
            log.error("[积分兑换] 订单 {} 系统通知发送失败, userId {}",
                    order.getOrderNo(), order.getUserId(), e);
        }
        emailNotificationService.sendPointExchangeSuccessEmail(order.getUserId().intValue(),
                order.getProductName(), order.getQuantity(), order.getTotalPoints(),
                order.getPriceAmount(), order.getOrderNo());
    }

    private String generateOrderNo() {
        return "P" + LocalDateTime.now().format(ORDER_NO_FORMATTER)
                + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
    }

    private static BigDecimal firstNotNull(BigDecimal first, BigDecimal second) {
        return first != null ? first : second;
    }

    private List<PointProductVO> toVOs(List<PointProduct> list) {
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }
        return list.stream().map(this::toVO).collect(Collectors.toList());
    }

    private List<PointProduct> readCache() {
        try {
            String json = redisTemplate.opsForValue().get(CACHE_KEY);
            if (json == null) {
                return null;
            }
            return objectMapper.readValue(json, new TypeReference<List<PointProduct>>() {});
        } catch (Exception e) {
            log.warn("[积分商品] 读取缓存失败，走 DB", e);
            return null;
        }
    }

    private void writeCache(List<PointProduct> list) {
        if (list == null) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(CACHE_KEY, objectMapper.writeValueAsString(list), CACHE_TTL);
        } catch (Exception e) {
            log.warn("[积分商品] 写入缓存失败", e);
        }
    }

    private void evictCache() {
        try {
            redisTemplate.delete(CACHE_KEY);
        } catch (Exception e) {
            log.warn("[积分商品] 缓存失效失败", e);
        }
    }
}
