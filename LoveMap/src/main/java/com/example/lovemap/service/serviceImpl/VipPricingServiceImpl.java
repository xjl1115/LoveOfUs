package com.example.lovemap.service.serviceImpl;

import com.example.lovemap.mapper.VipPricingMapper;
import com.example.lovemap.model.entity.VipPricing;
import com.example.lovemap.service.VipPricingService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * VIP 与次卡价格服务实现
 * <p>
 * 全部价格缓存到 Redis（key=vip:pricing:all），更新时主动失效。VIP 与次卡共用一份缓存。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VipPricingServiceImpl implements VipPricingService {

    /** Redis 缓存键：序列化后的 List<VipPricing> JSON */
    private static final String CACHE_KEY = "vip:pricing:all";

    /** 缓存有效期：1 小时，价格很少变动 */
    private static final Duration CACHE_TTL = Duration.ofHours(1);

    private final VipPricingMapper vipPricingMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public List<VipPricing> listAll() {
        List<VipPricing> cached = readCache();
        if (cached != null) {
            return cached;
        }
        List<VipPricing> list = vipPricingMapper.selectAll();
        writeCache(list);
        return list;
    }

    @Override
    public VipPricing findByCode(String productCode) {
        if (productCode == null) {
            return null;
        }
        for (VipPricing p : listAll()) {
            if (productCode.equals(p.getProductCode())) {
                return p;
            }
        }
        return null;
    }

    @Override
    public BigDecimal getVipTierPrice(int vipLevel) {
        for (VipPricing p : listAll()) {
            if (p.getVipLevel() != null && p.getVipLevel() == vipLevel) {
                return p.getPriceYuan();
            }
        }
        return null;
    }

    @Override
    public BigDecimal getQuotaUnitPrice(int quotaType) {
        for (VipPricing p : listAll()) {
            if (p.getQuotaType() != null && p.getQuotaType() == quotaType) {
                return p.getPriceYuan();
            }
        }
        return null;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updatePrices(List<PriceUpdate> updates) {
        if (updates == null || updates.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (PriceUpdate update : updates) {
            if (update.id() == null || update.priceYuan() == null) {
                continue;
            }
            // 价格必须为正，且不超过 1 万元（数据库 DECIMAL(10,2)，最多 99999999.99）
            if (update.priceYuan().signum() <= 0
                    || update.priceYuan().compareTo(new BigDecimal("99999.99")) > 0) {
                throw new IllegalArgumentException("价格必须大于 0 且不超过 99999.99 元，id=" + update.id());
            }
            total += vipPricingMapper.updatePrice(update.id(), update.priceYuan());
        }
        evictCache();
        log.info("[VIP定价] 批量更新 {} 条价格", total);
        return total;
    }

    /** 读取缓存：解析失败时返回 null，让上层走 DB */
    private List<VipPricing> readCache() {
        try {
            String json = redisTemplate.opsForValue().get(CACHE_KEY);
            if (json == null) {
                return null;
            }
            return objectMapper.readValue(json, new TypeReference<List<VipPricing>>() {});
        } catch (Exception e) {
            log.warn("[VIP定价] 读取缓存失败，走数据库", e);
            return null;
        }
    }

    /** 写缓存：失败仅记日志，不影响主流程 */
    private void writeCache(List<VipPricing> list) {
        if (list == null) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(list);
            redisTemplate.opsForValue().set(CACHE_KEY, json, CACHE_TTL);
        } catch (Exception e) {
            log.warn("[VIP定价] 写入缓存失败", e);
        }
    }

    /** 失效缓存：让下次读取重新走 DB */
    private void evictCache() {
        try {
            Boolean removed = redisTemplate.delete(CACHE_KEY);
            log.debug("[VIP定价] 缓存已失效, removed={}", removed);
        } catch (Exception e) {
            log.warn("[VIP定价] 缓存失效失败", e);
        }
    }

    /** 提供给 VipServiceImpl 校验用：在 init 时缓存命中失败时返回的快照 */
    public Map<String, BigDecimal> snapshotPriceMap() {
        Map<String, BigDecimal> map = new HashMap<>();
        for (VipPricing p : Objects.requireNonNullElse(listAll(), List.<VipPricing>of())) {
            map.put(p.getProductCode(), p.getPriceYuan());
        }
        return map;
    }
}
