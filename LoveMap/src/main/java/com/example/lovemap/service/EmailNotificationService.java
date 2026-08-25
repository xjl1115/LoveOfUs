package com.example.lovemap.service;

import com.example.lovemap.common.constant.UserConstant;
import com.example.lovemap.mapper.UserMapper;
import com.example.lovemap.model.entity.User;
import com.example.lovemap.model.vo.NotificationSettingsVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 邮件通知服务
 * 负责发送各类邮件通知
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailNotificationService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final AsyncMailService asyncMailService;
    private final UserMapper userMapper;

    /**
     * 异步发送邮件通知给伴侣
     *
     * @param userId 当前用户ID
     * @param action 动作描述（如"上传了一张新照片"）
     */
    @Async("notificationExecutor")
    public void sendEmailToPartner(Integer userId, String action) {
        try {
            User user = userMapper.selectById(userId);
            if (user == null) {
                log.warn("发送邮件通知失败：用户不存在, userId: {}", userId);
                return;
            }

            if (user.getIsBound() == null || user.getIsBound() != 1 || user.getPartnerId() == null) {
                log.debug("用户未绑定伴侣，跳过邮件通知, userId: {}", userId);
                return;
            }

            // 查询伴侣的通知设置
            String cacheKey = UserConstant.USER_NOTIFICATION_SETTINGS + user.getPartnerId();
            String cachedJson = redisTemplate.opsForValue().get(cacheKey);

            NotificationSettingsVO settings = null;
            if (cachedJson != null) {
                try {
                    settings = objectMapper.readValue(cachedJson, NotificationSettingsVO.class);
                } catch (Exception e) {
                    log.warn("解析伴侣通知设置失败, partnerId: {}, 使用默认值", user.getPartnerId(), e);
                }
            }

            // 检查是否开启照片相关通知
            if (settings != null && settings.getPhotoUpload() != null && !settings.getPhotoUpload()) {
                log.debug("伴侣未开启照片通知，跳过, userId: {}, partnerId: {}", userId, user.getPartnerId());
                return;
            }

            // 检查是否开启邮箱通知
            boolean emailEnabled = settings == null || settings.getEmail() == null || settings.getEmail();
            if (!emailEnabled) {
                log.debug("伴侣未开启邮箱通知，跳过, userId: {}, partnerId: {}", userId, user.getPartnerId());
                return;
            }

            // 查询伴侣信息
            User partner = userMapper.selectById(user.getPartnerId().intValue());
            if (partner == null || partner.getEmail() == null || partner.getEmail().isEmpty()) {
                log.warn("伴侣信息不存在或无邮箱, partnerId: {}", user.getPartnerId());
                return;
            }

            // 发送邮件
            String actor = user.getNickname() != null ? user.getNickname() : "您的伴侣";
            asyncMailService.sendSystemNotificationMailAsync(
                partner.getEmail(),
                partner.getNickname() != null ? partner.getNickname() : "用户",
                actor,
                action
            );

            log.info("已向伴侣发送邮件通知, userId: {}, partnerId: {}, action: {}", userId, user.getPartnerId(), action);
        } catch (Exception e) {
            log.error("发送伴侣邮件通知失败, userId: {}, action: {}", userId, action, e);
        }
    }

    /**
     * 异步发送纪念日提醒邮件
     *
     * @param userId 用户ID
     * @param anniversaryName 纪念日名称
     * @param daysUntil 距离天数
     * @param description 纪念日描述
     */
    @Async("notificationExecutor")
    public void sendAnniversaryReminderEmail(Integer userId, String anniversaryName, long daysUntil, String description) {
        try {
            User user = userMapper.selectById(userId);
            if (user == null) {
                log.warn("发送纪念日提醒邮件失败：用户不存在, userId: {}", userId);
                return;
            }

            // 检查是否开启纪念日提醒
            String cacheKey = UserConstant.USER_NOTIFICATION_SETTINGS + userId;
            String cachedJson = redisTemplate.opsForValue().get(cacheKey);

            NotificationSettingsVO settings = null;
            if (cachedJson != null) {
                try {
                    settings = objectMapper.readValue(cachedJson, NotificationSettingsVO.class);
                } catch (Exception e) {
                    log.warn("解析用户通知设置失败, userId: {}, 使用默认值", userId, e);
                }
            }

            if (settings != null && settings.getAnniversary() != null && !settings.getAnniversary()) {
                log.debug("用户未开启纪念日提醒，跳过, userId: {}", userId);
                return;
            }

            if (settings != null && settings.getEmail() != null && !settings.getEmail()) {
                log.debug("用户未开启邮箱通知，跳过纪念日提醒, userId: {}", userId);
                return;
            }

            if (user.getEmail() == null || user.getEmail().isEmpty()) {
                log.warn("用户邮箱为空，跳过纪念日提醒, userId: {}", userId);
                return;
            }

            String nickname = user.getNickname() != null ? user.getNickname() : "用户";
            asyncMailService.sendAnniversaryReminderMailAsync(
                user.getEmail(),
                nickname,
                anniversaryName,
                daysUntil,
                description
            );

            log.info("已向用户发送纪念日提醒邮件, userId: {}, anniversary: {}, daysUntil: {}", userId, anniversaryName, daysUntil);
        } catch (Exception e) {
            log.error("发送纪念日提醒邮件失败, userId: {}, anniversary: {}", userId, anniversaryName, e);
        }
    }
}
