package com.example.lovemap.service.serviceImpl;

import com.example.lovemap.common.Result;
import com.example.lovemap.common.ResultCode;
import com.example.lovemap.common.constant.NotificationConstant;
import com.example.lovemap.mapper.NotificationMapper;
import com.example.lovemap.model.entity.Notification;
import com.example.lovemap.model.vo.NotificationListVO;
import com.example.lovemap.model.vo.NotificationVO;
import com.example.lovemap.service.NotificationService;
import com.example.lovemap.service.SseService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 通知服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationMapper notificationMapper;
    private final SseService sseService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 查询通知列表
     */
    @Override
    public Result<NotificationListVO> getNotificationList(Integer userId, Integer page, Integer size, Integer isRead) {
        if (page == null || page < 1) {
            page = 1;
        }
        if (size == null || size < 1) {
            size = 20;
        }
        if (size > 100) {
            size = 100;
        }

        String cacheKey;
        if (isRead != null && isRead == 1) {
            cacheKey = NotificationConstant.NOTIFICATION_READ_PREFIX + userId;
        } else {
            cacheKey = NotificationConstant.NOTIFICATION_UNREAD_PREFIX + userId;
        }

        // 尝试从 Redis 缓存获取
        List<NotificationVO> cachedList = getFromCache(cacheKey);
        if (cachedList != null) {
            int total = cachedList.size();
            int fromIndex = (page - 1) * size;
            int toIndex = Math.min(fromIndex + size, total);
            List<NotificationVO> pagedList = fromIndex < total ? cachedList.subList(fromIndex, toIndex) : new ArrayList<>();

            Long unreadCount = getUnreadCountFromCache(userId);
            if (unreadCount == null) {
                unreadCount = notificationMapper.countUnreadByUserId(userId);
                putUnreadCountToCache(userId, unreadCount);
            }

            NotificationListVO listVO = new NotificationListVO();
            listVO.setTotal((long) total);
            listVO.setUnreadCount(unreadCount);
            listVO.setList(pagedList);
            return Result.success(listVO);
        }

        // 缓存未命中，从数据库查询
        PageHelper.startPage(page, size);
        List<Notification> notifications = notificationMapper.selectByUserId(userId, isRead);
        PageInfo<Notification> pageInfo = new PageInfo<>(notifications);

        Long unreadCount = notificationMapper.countUnreadByUserId(userId);

        List<NotificationVO> voList = pageInfo.getList().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());

        // 存入 Redis 缓存
        putToCache(cacheKey, voList);
        putUnreadCountToCache(userId, unreadCount);

        NotificationListVO listVO = new NotificationListVO();
        listVO.setTotal(pageInfo.getTotal());
        listVO.setUnreadCount(unreadCount);
        listVO.setList(voList);

        return Result.success(listVO);
    }

    /**
     * 查询未读通知数量
     */
    @Override
    public Result<Long> getUnreadCount(Integer userId) {
        Long count = getUnreadCountFromCache(userId);
        if (count == null) {
            count = notificationMapper.countUnreadByUserId(userId);
            putUnreadCountToCache(userId, count);
        }
        return Result.success(count);
    }

    /**
     * 标记通知为已读
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> markAsRead(Integer userId, Integer notificationId) {
        int affected = notificationMapper.markAsRead(notificationId, userId);
        if (affected == 0) {
            return Result.error(ResultCode.NOT_FOUND, "通知不存在或无权操作");
        }

        // 从 key1（未读缓存）中删除该消息
        removeFromCache(NotificationConstant.NOTIFICATION_UNREAD_PREFIX + userId, notificationId);

        // 将该消息（标记为已读）存入 key2（已读缓存）
        Notification notification = notificationMapper.selectByIdAndUserId(notificationId, userId);
        if (notification != null) {
            NotificationVO vo = convertToVO(notification);
            addToReadCache(userId, vo);
        }

        // 更新未读数缓存 + 推送 SSE unread-count（右上角角标实时刷新）
        decrementUnreadCount(userId);
        pushUnreadCountSSE(userId);

        return Result.success(null);
    }

    /**
     * 标记所有通知为已读
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Integer> markAllAsRead(Integer userId) {
        // 查询所有未读通知
        List<Notification> unreadNotifications = notificationMapper.selectByUserId(userId, 0);

        int affected = notificationMapper.markAllAsRead(userId);

        if (affected > 0) {
            // 清空未读缓存
            redisTemplate.delete(NotificationConstant.NOTIFICATION_UNREAD_PREFIX + userId);
            // 清空未读数缓存
            redisTemplate.delete(NotificationConstant.NOTIFICATION_UNREAD_COUNT_PREFIX + userId);

            // 将已读消息添加到已读缓存
            String readKey = NotificationConstant.NOTIFICATION_READ_PREFIX + userId;
            List<NotificationVO> cachedReadList = getFromCache(readKey);
            if (cachedReadList == null) {
                cachedReadList = new ArrayList<>();
            }
            for (Notification n : unreadNotifications) {
                NotificationVO vo = convertToVO(n);
                vo.setIsRead(1);
                cachedReadList.add(0, vo);
            }
            putToCache(readKey, cachedReadList);

            // 推送 SSE unread-count（清零 + 列表缓存已重置，下次拉取即新状态）
            pushUnreadCountSSE(userId);
        }

        return Result.success(affected);
    }

    /**
     * 删除通知
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> deleteNotification(Integer userId, Integer notificationId) {
        // 先查通知是否存在
        Notification notification = notificationMapper.selectByIdAndUserId(notificationId, userId);
        if (notification == null) {
            return Result.error(ResultCode.NOT_FOUND, "通知不存在或无权删除");
        }

        int affected = notificationMapper.deleteById(notificationId, userId);
        if (affected == 0) {
            return Result.error(ResultCode.NOT_FOUND, "通知不存在或无权删除");
        }

        // 从 Redis 缓存中同时删除
        removeFromCache(NotificationConstant.NOTIFICATION_UNREAD_PREFIX + userId, notificationId);
        removeFromCache(NotificationConstant.NOTIFICATION_READ_PREFIX + userId, notificationId);

        // 如果是未读消息，更新未读数缓存 + 推送 SSE unread-count
        if (notification.getIsRead() == 0) {
            decrementUnreadCount(userId);
            pushUnreadCountSSE(userId);
        }

        return Result.success(null);
    }

    /**
     * 清空已读通知
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Integer> clearReadNotifications(Integer userId) {
        int affected = notificationMapper.deleteReadByUserId(userId);

        if (affected > 0) {
            // 清空已读缓存
            redisTemplate.delete(NotificationConstant.NOTIFICATION_READ_PREFIX + userId);
        }

        return Result.success(affected);
    }

    /**
     * 创建通知（存入数据库）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Notification createNotification(Integer userId, String text) {
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setText(text);
        notification.setIsRead(0);
        notification.setCreatedAt(LocalDateTime.now().format(FORMATTER));

        notificationMapper.insert(notification);
        log.info("创建通知成功, userId: {}, text: {}", userId, text);

        return notification;
    }

    /**
     * 转换为 VO
     */
    private NotificationVO convertToVO(Notification notification) {
        NotificationVO vo = new NotificationVO();
        vo.setId(notification.getId());
        vo.setText(notification.getText());
        vo.setUserId(notification.getUserId());
        vo.setIsRead(notification.getIsRead());
        vo.setCreatedAt(notification.getCreatedAt());
        return vo;
    }

    /**
     * 创建通知并推送SSE
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createAndPushNotification(Integer userId, String text) {
        // 创建通知记录
        Notification notification = createNotification(userId, text);

        // 转换为 VO
        NotificationVO vo = convertToVO(notification);

        // 推送SSE通知（新通知事件，前端 SystemMessageFloatBtn 会更新列表）
        sseService.pushNotification(userId, vo);

        // 存入 Redis 未读缓存（key1）
        addToUnreadCache(userId, vo);

        // 更新未读数缓存 + 推送 SSE unread-count（前端右上角角标实时刷新）
        incrementUnreadCount(userId);
        pushUnreadCountSSE(userId);

        log.info("创建并推送通知成功, userId: {}, text: {}", userId, text);
    }

    /**
     * 推送 unread-count SSE 事件（前端 SystemMessageFloatBtn 已监听，用于实时刷新右上角未读角标）
     * <p>
     * 设计要点：
     * <ul>
     *   <li>从 Redis 缓存读 count；缓存缺失时回退 DB 查询并回填缓存</li>
     *   <li>SSE 推送失败不影响主流程（仅日志 warn）</li>
     *   <li>调用方在修改未读数缓存（+/-）之后调用本方法</li>
     * </ul>
     */
    private void pushUnreadCountSSE(Integer userId) {
        if (userId == null) return;
        try {
            Long count = getUnreadCountFromCache(userId);
            if (count == null) {
                count = notificationMapper.countUnreadByUserId(userId);
                putUnreadCountToCache(userId, count);
            }
            Map<String, Object> payload = new HashMap<>();
            payload.put("count", count);
            sseService.sendEvent(userId, "unread-count", payload);
        } catch (Exception e) {
            log.warn("通知 unread-count SSE 推送失败, userId: {}", userId, e);
        }
    }

    // ==================== Redis 缓存辅助方法 ====================

    /**
     * 从 Redis 缓存获取通知列表
     */
    private List<NotificationVO> getFromCache(String key) {
        String json = redisTemplate.opsForValue().get(key);
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<NotificationVO>>() {});
        } catch (JsonProcessingException e) {
            log.warn("反序列化缓存失败, key: {}", key, e);
            return null;
        }
    }

    /**
     * 将通知列表存入 Redis 缓存
     */
    private void putToCache(String key, List<NotificationVO> list) {
        try {
            String json = objectMapper.writeValueAsString(list);
            redisTemplate.opsForValue().set(key, json);
        } catch (JsonProcessingException e) {
            log.warn("序列化缓存失败, key: {}", key, e);
        }
    }

    /**
     * 获取未读数缓存
     */
    private Long getUnreadCountFromCache(Integer userId) {
        String key = NotificationConstant.NOTIFICATION_UNREAD_COUNT_PREFIX + userId;
        String value = redisTemplate.opsForValue().get(key);
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 设置未读数缓存
     */
    private void putUnreadCountToCache(Integer userId, Long count) {
        String key = NotificationConstant.NOTIFICATION_UNREAD_COUNT_PREFIX + userId;
        redisTemplate.opsForValue().set(key, String.valueOf(count));
    }

    /**
     * 添加消息到未读缓存（key1）
     */
    private void addToUnreadCache(Integer userId, NotificationVO vo) {
        String key = NotificationConstant.NOTIFICATION_UNREAD_PREFIX + userId;
        List<NotificationVO> cachedList = getFromCache(key);
        if (cachedList == null) {
            cachedList = new ArrayList<>();
        }
        cachedList.add(0, vo);
        putToCache(key, cachedList);
    }

    /**
     * 添加消息到已读缓存（key2）
     */
    private void addToReadCache(Integer userId, NotificationVO vo) {
        String key = NotificationConstant.NOTIFICATION_READ_PREFIX + userId;
        List<NotificationVO> cachedList = getFromCache(key);
        if (cachedList == null) {
            cachedList = new ArrayList<>();
        }
        cachedList.add(0, vo);
        putToCache(key, cachedList);
    }

    /**
     * 从缓存中按通知ID删除消息
     */
    private void removeFromCache(String key, Integer notificationId) {
        List<NotificationVO> cachedList = getFromCache(key);
        if (cachedList != null) {
            cachedList.removeIf(vo -> vo.getId().equals(notificationId));
            putToCache(key, cachedList);
        }
    }

    /**
     * 增加未读数缓存
     */
    private void incrementUnreadCount(Integer userId) {
        String key = NotificationConstant.NOTIFICATION_UNREAD_COUNT_PREFIX + userId;
        redisTemplate.opsForValue().increment(key);
    }

    /**
     * 减少未读数缓存
     */
    private void decrementUnreadCount(Integer userId) {
        String key = NotificationConstant.NOTIFICATION_UNREAD_COUNT_PREFIX + userId;
        Long current = redisTemplate.opsForValue().increment(key, -1);
        if (current != null && current < 0) {
            redisTemplate.delete(key);
        }
    }
}
