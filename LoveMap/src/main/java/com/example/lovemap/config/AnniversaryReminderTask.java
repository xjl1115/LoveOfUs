package com.example.lovemap.config;

import com.example.lovemap.common.constant.AnniversaryConstant;
import com.example.lovemap.common.constant.NotificationConstant;
import com.example.lovemap.common.constant.UserConstant;
import com.example.lovemap.mapper.AnniversaryMapper;
import com.example.lovemap.mapper.UserMapper;
import com.example.lovemap.model.entity.Anniversary;
import com.example.lovemap.model.entity.User;
import com.example.lovemap.model.vo.NotificationSettingsVO;
import com.example.lovemap.service.AsyncMailService;
import com.example.lovemap.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 纪念日提醒定时任务
 * 每天检查即将到达的纪念日，给用户发送邮箱通知
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AnniversaryReminderTask {

    private final AnniversaryMapper anniversaryMapper;
    private final UserMapper userMapper;
    private final AsyncMailService asyncMailService;
    private final NotificationService notificationService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 每天凌晨0点执行，检查未来7天内的纪念日
     */
    @Scheduled(cron = "0 0 0 * * ?")
    public void checkAnniversaryReminder() {
        log.info("开始执行纪念日提醒检查");
        
        try {
            // 查询所有用户
            List<User> allUsers = userMapper.selectAll();
            
            for (User user : allUsers) {
                
                // 检查用户是否开启了纪念日提醒
                if (!isAnniversaryNotificationEnabled(user.getId().intValue())) {
                    log.debug("用户 {} 未开启纪念日提醒，跳过", user.getId());
                    continue;
                }
                
                // 检查用户是否开启了邮箱通知
                if (!isEmailNotificationEnabled(user.getId().intValue())) {
                    log.debug("用户 {} 未开启邮箱通知，跳过", user.getId());
                    continue;
                }
                
                // 检查用户邮箱是否存在
                if (user.getEmail() == null || user.getEmail().isEmpty()) {
                    log.warn("用户 {} 邮箱为空，跳过纪念日提醒", user.getId());
                    continue;
                }
                
                // 查询该用户可见的纪念日（情侣组共享 + 未绑定情侣时的个人纪念日）
                List<Anniversary> anniversaries = anniversaryMapper.selectByGroupOrUser(user.getGroupId(), user.getId());
                
                for (Anniversary anniversary : anniversaries) {
                    checkAndSendReminder(user, anniversary);
                }
            }
            
            log.info("纪念日提醒检查完成");
            // 整轮跑完才标记当天已执行：中途失败（如数据库不可用）不标记，重启后仍可补跑
            markReminderRunToday();
        } catch (Exception e) {
            log.error("纪念日提醒检查失败", e);
        }
    }

    /**
     * 启动补跑：若当天 0 点的提醒检查没有执行过（例如 0 点服务掉线），启动后立即补一次。
     * <p>
     * 通过 Redis 标记 {@link AnniversaryConstant#REMIND_LAST_RUN_KEY} 保证每个自然日最多执行一次：
     * 当天已执行过则跳过，避免每次重启重复给用户发提醒。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void catchUpMissedReminder() {
        String today = LocalDate.now().toString();
        try {
            String lastRun = redisTemplate.opsForValue().get(AnniversaryConstant.REMIND_LAST_RUN_KEY);
            if (today.equals(lastRun)) {
                log.info("纪念日提醒今日已执行，跳过启动补跑");
                return;
            }
            log.info("纪念日提醒今日未执行（上次: {}），启动补跑", lastRun);
            checkAnniversaryReminder();
        } catch (Exception e) {
            log.error("纪念日提醒启动补跑失败", e);
        }
    }

    /**
     * 写入"提醒检查已执行"标记（保留 7 天，足够覆盖任何跨天判断）
     */
    private void markReminderRunToday() {
        try {
            redisTemplate.opsForValue().set(AnniversaryConstant.REMIND_LAST_RUN_KEY,
                    LocalDate.now().toString(), 7, TimeUnit.DAYS);
        } catch (Exception e) {
            log.warn("写入纪念日提醒执行标记失败", e);
        }
    }

    /**
     * 检查单个纪念日是否需要发送提醒
     */
    private void checkAndSendReminder(User user, Anniversary anniversary) {
        LocalDate today = LocalDate.now();
        LocalDate anniversaryDate = anniversary.getAnniversaryDate();
        
        // 计算距离纪念日的天数
        long daysUntil;
        if (anniversary.getIsRecurring()) {
            // 如果是每年重复的纪念日，计算今年的纪念日
            LocalDate thisYearAnniversary = anniversaryDate.withYear(today.getYear());
            if (thisYearAnniversary.isBefore(today)) {
                // 如果今年的纪念日已过，计算明年的
                thisYearAnniversary = anniversaryDate.withYear(today.getYear() + 1);
            }
            daysUntil = ChronoUnit.DAYS.between(today, thisYearAnniversary);
        } else {
            daysUntil = ChronoUnit.DAYS.between(today, anniversaryDate);
        }
        
        // 检查是否在提醒范围内（提前7天、3天、1天、当天）
        Integer remindDays = anniversary.getRemindDays();
        if (remindDays == null) {
            remindDays = 7; // 默认提前7天提醒
        }
        
        if (daysUntil >= 0 && daysUntil <= remindDays) {
            sendAnniversaryReminder(user, anniversary, daysUntil);
        }
    }

    /**
     * 发送纪念日提醒邮件和 SSE 通知
     */
    private void sendAnniversaryReminder(User user, Anniversary anniversary, long daysUntil) {
        try {
            String nickname = user.getNickname() != null ? user.getNickname() : "用户";
            String anniversaryName = anniversary.getName();
            String description = anniversary.getDescription();
            
            // 构建通知文本
            String notificationText;
            if (daysUntil == 0) {
                notificationText = "今天是 " + anniversaryName + "！";
            } else {
                notificationText = "距离 " + anniversaryName + " 还有 " + daysUntil + " 天";
            }
            
            // 异步发送邮件
            asyncMailService.sendAnniversaryReminderMailAsync(
                user.getEmail(),
                nickname,
                anniversaryName,
                daysUntil,
                description
            );
            
            // 发送 SSE 通知并存入数据库（user_id 为用户本人 ID）
            // type=纪念日提醒 + businessId=纪念日ID，前端据此跳转对应纪念日详情
            notificationService.createAndPushNotification(user.getId().intValue(), notificationText,
                    NotificationConstant.TYPE_ANNIVERSARY, anniversary.getId());
            
            log.info("已向用户 {} 发送纪念日提醒: {}, 距离{}天", user.getId(), anniversaryName, daysUntil);
        } catch (Exception e) {
            log.error("发送纪念日提醒失败, userId: {}, anniversaryId: {}", 
                user.getId(), anniversary.getId(), e);
        }
    }

    /**
     * 检查用户是否开启了纪念日提醒
     *
     * @param userId 用户ID
     * @return 是否开启
     */
    private boolean isAnniversaryNotificationEnabled(Integer userId) {
        String cacheKey = UserConstant.USER_NOTIFICATION_SETTINGS + userId;
        String cachedJson = redisTemplate.opsForValue().get(cacheKey);

        if (cachedJson == null) {
            // 默认开启
            return true;
        }

        try {
            NotificationSettingsVO settings = objectMapper.readValue(cachedJson, NotificationSettingsVO.class);
            return settings.getAnniversary() == null || Boolean.TRUE.equals(settings.getAnniversary());
        } catch (Exception e) {
            log.warn("解析用户通知设置失败, userId: {}, 使用默认值", userId, e);
            return true;
        }
    }

    /**
     * 检查用户是否开启了邮箱通知
     *
     * @param userId 用户ID
     * @return 是否开启
     */
    private boolean isEmailNotificationEnabled(Integer userId) {
        String cacheKey = UserConstant.USER_NOTIFICATION_SETTINGS + userId;
        String cachedJson = redisTemplate.opsForValue().get(cacheKey);

        if (cachedJson == null) {
            // 默认开启
            return true;
        }

        try {
            NotificationSettingsVO settings = objectMapper.readValue(cachedJson, NotificationSettingsVO.class);
            return settings.getEmail() == null || Boolean.TRUE.equals(settings.getEmail());
        } catch (Exception e) {
            log.warn("解析用户通知设置失败, userId: {}, 使用默认值", userId, e);
            return true;
        }
    }
}
