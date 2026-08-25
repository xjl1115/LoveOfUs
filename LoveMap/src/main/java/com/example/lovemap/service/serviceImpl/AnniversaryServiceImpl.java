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
            if (ctx.groupId == null) {
                return Result.success(Collections.emptyList());
            }

            String cacheKey = AnniversaryConstant.ANNIVERSARY_LIST_PREFIX + ctx.groupId;

            // 尝试从缓存获取
            List<AnniversaryVO> cached = getFromCache(cacheKey, new TypeReference<List<AnniversaryVO>>() {});
            if (cached != null) {
                return Result.success(cached);
            }

            // 查询数据库
            List<Anniversary> list = anniversaryMapper.selectByGroupId(ctx.groupId);
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

            anniversaryMapper.insert(anniversary);

            // 清除列表缓存
            clearListCache(ctx.groupId);

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
            clearListCache(ctx.groupId);
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
            clearListCache(ctx.groupId);
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

        UserContext(User user) {
            this.user = user;
            this.groupId = user.getGroupId();
        }
    }

    /**
     * 带用户校验的执行模板
     * 校验用户存在且已绑定情侣关系
     */
    private <T> Result<T> withUserValidation(Integer userId, Function<UserContext, Result<T>> action) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return Result.error(ResultCode.NOT_FOUND, "用户不存在");
        }
        if (user.getGroupId() == null) {
            return Result.error(ResultCode.FORBIDDEN, "请先绑定情侣关系");
        }
        return action.apply(new UserContext(user));
    }

    /**
     * 带纪念日校验的执行模板
     * 校验用户存在、已绑定、纪念日存在且属于该群组
     */
    private <T> Result<T> withAnniversaryValidation(Integer userId, Long id, 
            AnniversaryAction<T> action) {
        return withUserValidation(userId, ctx -> {
            Anniversary existing = anniversaryMapper.selectByIdAndGroupId(id, ctx.groupId);
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
     * 将数据存入缓存（带1天过期时间）
     */
    private void putToCache(String cacheKey, Object data) {
        try {
            String json = objectMapper.writeValueAsString(data);
            redisTemplate.opsForValue().set(cacheKey, json, 1, TimeUnit.DAYS);
        } catch (JsonProcessingException e) {
            log.warn("序列化缓存失败, key: {}", cacheKey, e);
        }
    }

    /**
     * 清除纪念日列表缓存
     */
    private void clearListCache(Long groupId) {
        String cacheKey = AnniversaryConstant.ANNIVERSARY_LIST_PREFIX + groupId;
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