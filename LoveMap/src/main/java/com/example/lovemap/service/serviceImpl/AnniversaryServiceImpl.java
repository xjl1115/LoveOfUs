package com.example.lovemap.service.serviceImpl;

import com.example.lovemap.common.Result;
import com.example.lovemap.common.ResultCode;
import com.example.lovemap.common.constant.AnniversaryConstant;
import com.example.lovemap.mapper.AnniversaryMapper;
import com.example.lovemap.mapper.UserMapper;
import com.example.lovemap.model.dto.AnniversaryDTO;
import com.example.lovemap.model.entity.Anniversary;
import com.example.lovemap.model.entity.User;
import com.example.lovemap.model.vo.AnniversaryVO;
import com.example.lovemap.service.AnniversaryService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 纪念日服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnniversaryServiceImpl implements AnniversaryService {

    /** 上海时区，与 application.yml 及定时任务保持一致 */
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final AnniversaryMapper anniversaryMapper;
    private final UserMapper userMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 列出纪念日列表
     */
    @Override
    public Result<List<AnniversaryVO>> listAnniversaries(Integer userId) {
        return withUserValidation(userId, ctx -> {
            String cacheKey = listCacheKey(ctx);

            // 尝试从缓存获取
            List<AnniversaryVO> cached = getFromCache(cacheKey, new TypeReference<List<AnniversaryVO>>() {});
            if (cached != null) {
                return Result.success(cached);
            }

            // 查询数据库
            List<Anniversary> list = anniversaryMapper.selectByGroupOrUser(ctx.groupId, ctx.userId);
            List<AnniversaryVO> voList = convertToVOList(list);

            // 存入缓存
            putToCache(cacheKey, voList);

            return Result.success(voList);
        });
    }

    /**
     * 获取纪念日详情
     */
    @Override
    public Result<AnniversaryVO> getAnniversaryDetail(Integer userId, Long id) {
        return withAnniversaryValidation(userId, id, (ctx, anniversary) -> {
            String cacheKey = AnniversaryConstant.ANNIVERSARY_DETAIL_PREFIX + id;

            // 尝试从缓存获取
            AnniversaryVO cached = getFromCache(cacheKey, AnniversaryVO.class);
            if (cached != null) {
                return Result.success(cached);
            }

            AnniversaryVO vo = convertToVO(anniversary);

            // 存入缓存
            putToCache(cacheKey, vo);

            return Result.success(vo);
        });
    }

    /**
     * 创建纪念日
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<AnniversaryVO> createAnniversary(Integer userId, AnniversaryDTO dto) {
        return withUserValidation(userId, ctx -> {
            Anniversary anniversary = new Anniversary();
            copyDtoToEntity(dto, anniversary);
            anniversary.setGroupId(ctx.groupId);
            anniversary.setUserId(ctx.userId);

            anniversaryMapper.insert(anniversary);

            // 清除列表缓存
            clearListCache(ctx);

            return Result.success(convertToVO(anniversary));
        });
    }

    /**
     * 更新纪念日
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<AnniversaryVO> updateAnniversary(Integer userId, Long id, AnniversaryDTO dto) {
        return withAnniversaryValidation(userId, id, (ctx, existing) -> {
            copyDtoToEntity(dto, existing);

            anniversaryMapper.update(existing);

            // 清除相关缓存
            clearListCache(ctx);
            clearDetailCache(id);

            return Result.success(convertToVO(existing));
        });
    }

    /**
     * 删除纪念日
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> deleteAnniversary(Integer userId, Long id) {
        return withAnniversaryValidation(userId, id, (ctx, existing) -> {
            anniversaryMapper.deleteById(id);

            // 清除相关缓存
            clearListCache(ctx);
            clearDetailCache(id);

            return Result.success(null);
        });
    }

    // ==================== 校验模板方法 ====================

    /**
     * 用户上下文
     */
    private static class UserContext {
        final User user;
        final Long groupId;
        final Long userId;

        UserContext(User user) {
            this.user = user;
            this.groupId = user.getGroupId();
            this.userId = user.getId();
        }
    }

    /**
     * 带用户校验的执行模板
     * 只校验用户存在；是否绑定情侣不影响使用，未绑定时走个人数据
     */
    private <T> Result<T> withUserValidation(Integer userId, Function<UserContext, Result<T>> action) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return Result.error(ResultCode.NOT_FOUND, "用户不存在");
        }
        return action.apply(new UserContext(user));
    }

    /**
     * 带纪念日校验的执行模板
     * 校验用户存在、纪念日存在且落在自己的归属范围（情侣组或个人）
     */
    private <T> Result<T> withAnniversaryValidation(Integer userId, Long id, 
            AnniversaryAction<T> action) {
        return withUserValidation(userId, ctx -> {
            Anniversary existing = anniversaryMapper.selectByIdAndGroupOrUser(id, ctx.groupId, ctx.userId);
            if (existing == null) {
                return Result.error(ResultCode.NOT_FOUND, "纪念日不存在");
            }
            return action.execute(ctx, existing);
        });
    }

    /**
     * 纪念日操作函数式接口
     */
    @FunctionalInterface
    private interface AnniversaryAction<T> {
        Result<T> execute(UserContext ctx, Anniversary anniversary);
    }

    // ==================== 缓存操作方法 ====================

    /**
     * 从缓存获取数据（普通类型）
     */
    private <T> T getFromCache(String cacheKey, Class<T> clazz) {
        String cachedJson = redisTemplate.opsForValue().get(cacheKey);
        if (cachedJson != null) {
            try {
                return objectMapper.readValue(cachedJson, clazz);
            } catch (JsonProcessingException e) {
                log.warn("反序列化缓存失败, key: {}", cacheKey, e);
            }
        }
        return null;
    }

    /**
     * 从缓存获取数据（泛型类型）
     */
    private <T> T getFromCache(String cacheKey, TypeReference<T> typeRef) {
        String cachedJson = redisTemplate.opsForValue().get(cacheKey);
        if (cachedJson != null) {
            try {
                return objectMapper.readValue(cachedJson, typeRef);
            } catch (JsonProcessingException e) {
                log.warn("反序列化缓存失败, key: {}", cacheKey, e);
            }
        }
        return null;
    }

    /**
     * 将数据存入缓存，TTL 到当天 24:00（Asia/Shanghai）为止。
     * <p>
     * 缓存里存的是写入时刻算出的 daysUntil，若用固定 1 天 TTL，
     * 跨天后仍会命中旧值（倒计时不动），只有 0 点的缓存清理任务跑过才会纠正；
     * 改为凌晨自动过期后，即使 0 点服务掉线，跨天后首次请求也会重新计算。
     */
    private void putToCache(String cacheKey, Object data) {
        try {
            String json = objectMapper.writeValueAsString(data);
            redisTemplate.opsForValue().set(cacheKey, json, secondsUntilNextMidnight(), TimeUnit.SECONDS);
        } catch (JsonProcessingException e) {
            log.warn("序列化缓存失败, key: {}", cacheKey, e);
        }
    }

    /**
     * 距离下一个 0 点（Asia/Shanghai）的秒数，最少 60 秒，避免临界写入立刻失效
     */
    private long secondsUntilNextMidnight() {
        LocalDateTime now = LocalDateTime.now(ZONE);
        long seconds = ChronoUnit.SECONDS.between(now, now.toLocalDate().plusDays(1).atStartOfDay());
        return Math.max(seconds, 60);
    }

    /**
     * 纪念日列表缓存 key：绑定时按情侣组，未绑定时按用户个人（加前缀避免组 ID 与用户 ID 撞号）
     */
    private String listCacheKey(UserContext ctx) {
        return AnniversaryConstant.ANNIVERSARY_LIST_PREFIX
                + (ctx.groupId != null ? "g" + ctx.groupId : "u" + ctx.userId);
    }

    /**
     * 清除纪念日列表缓存
     */
    private void clearListCache(UserContext ctx) {
        String cacheKey = listCacheKey(ctx);
        redisTemplate.delete(cacheKey);
        log.debug("已清除纪念日列表缓存, key: {}", cacheKey);
    }

    /**
     * 清除纪念日详情缓存
     */
    private void clearDetailCache(Long id) {
        String cacheKey = AnniversaryConstant.ANNIVERSARY_DETAIL_PREFIX + id;
        redisTemplate.delete(cacheKey);
        log.debug("已清除纪念日详情缓存, key: {}", cacheKey);
    }

    // ==================== 转换方法 ====================

    /**
     * 复制 DTO 字段到实体
     */
    private void copyDtoToEntity(AnniversaryDTO dto, Anniversary entity) {
        entity.setName(dto.getName());
        entity.setAnniversaryDate(dto.getAnniversaryDate());
        entity.setIsRecurring(dto.getIsRecurring() != null ? dto.getIsRecurring() : false);
        entity.setRemindDays(dto.getRemindDays() != null ? dto.getRemindDays() : 0);
        entity.setDescription(dto.getDescription());
    }

    /**
     * 实体转 VO
     */
    private AnniversaryVO convertToVO(Anniversary anniversary) {
        AnniversaryVO vo = new AnniversaryVO();
        vo.setId(anniversary.getId());
        vo.setName(anniversary.getName());
        vo.setAnniversaryDate(anniversary.getAnniversaryDate());
        vo.setIsRecurring(anniversary.getIsRecurring());
        vo.setRemindDays(anniversary.getRemindDays());
        vo.setDescription(anniversary.getDescription());
        vo.setCreatedAt(anniversary.getCreatedAt());
        vo.setUpdatedAt(anniversary.getUpdatedAt());
        vo.calculateDaysUntil();
        return vo;
    }

    /**
     * 批量实体转 VO（按 daysUntil 升序排列）
     */
    private List<AnniversaryVO> convertToVOList(List<Anniversary> list) {
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }
        return list.stream()
                .map(this::convertToVO)
                .sorted(Comparator.comparing(
                        AnniversaryVO::getDaysUntil,
                        Comparator.nullsLast(Comparator.naturalOrder())
                ))
                .collect(Collectors.toList());
    }
}
