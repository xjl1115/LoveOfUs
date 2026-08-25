package com.example.lovemap.service.serviceImpl;

import com.example.lovemap.common.Result;
import com.example.lovemap.common.ResultCode;
import com.example.lovemap.common.ServiceHelper;
import com.example.lovemap.common.constant.CaptchaConstant;
import com.example.lovemap.common.constant.UserConstant;
import com.example.lovemap.mapper.UserMapper;
import com.example.lovemap.model.dto.NotificationSettingsDTO;
import com.example.lovemap.model.dto.UserProfileUpdateDTO;
import com.example.lovemap.model.entity.User;
import com.example.lovemap.model.vo.BindCodeVO;
import com.example.lovemap.model.vo.NotificationSettingsVO;
import com.example.lovemap.model.vo.PartnerVO;
import com.example.lovemap.model.vo.UserStatsVO;
import com.example.lovemap.model.vo.UserVO;
import com.example.lovemap.service.UserService;
import com.example.lovemap.utils.AliyunOSSUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final StringRedisTemplate redisTemplate;
    private final PasswordEncoder passwordEncoder;
    private final AliyunOSSUtils aliyunOSSUtils;
    private final ObjectMapper objectMapper;

    /**
     * 获取用户信息
     */
    @Override
    public Result<UserVO> getUserInfo(Integer userId) {
        // 2. 查询数据库（先查用户获取 groupId）
        User user = userMapper.selectById(userId);
        if (user == null) {
            return Result.error(ResultCode.NOT_FOUND, "用户不存在");
        }

        // 缓存 key 必须按 userId 区分：情侣共享 groupId，用 groupId 拼会导致 A/B 互相看到对方的 UserVO
        String cacheKey = UserConstant.USER_INFO + user.getId();

        // 1. 尝试从 Redis 缓存获取
        UserVO cached = ServiceHelper.getFromCache(redisTemplate, objectMapper, cacheKey, UserVO.class);
        if (cached != null) {
            return Result.success(cached);
        }

        // 3. 构建返回数据
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setNickname(user.getNickname());
        vo.setAvatarUrl(user.getAvatarUrl());
        vo.setPhone(maskPhone(user.getPhone()));
        vo.setEmail(user.getEmail());
        vo.setIsBound(user.getIsBound() != null && user.getIsBound() == 1);
        vo.setRelationshipStart(user.getRelationshipStart());
        vo.setGroupId(user.getGroupId());

        // 4. 计算在一起天数
        if (user.getRelationshipStart() != null) {
            long days = ChronoUnit.DAYS.between(user.getRelationshipStart(), LocalDate.now());
            vo.setDaysTogether((int) days);
        }

        // 5. 绑定码信息（从 Redis 获取）
        BindCodeVO bindCodeVO = new BindCodeVO();
        String bindRedisKey = ServiceHelper.buildCacheKey(CaptchaConstant.BIND_CODE_PREFIX, user.getGroupId(), user.getId());
        String code = redisTemplate.opsForValue().get(bindRedisKey);
        bindCodeVO.setCode(code);
        bindCodeVO.setIsBound(user.getIsBound() != null && user.getIsBound() == 1);
        vo.setBindCode(bindCodeVO);

        // 6. 伴侣信息（已绑定时）
        if (user.getPartnerId() != null) {
            User partner = userMapper.selectById(user.getPartnerId().intValue());
            if (partner != null) {
                PartnerVO partnerVO = new PartnerVO();
                partnerVO.setId(partner.getId());
                partnerVO.setNickname(partner.getNickname());
                partnerVO.setAvatarUrl(partner.getAvatarUrl());
                partnerVO.setPhone(maskPhone(partner.getPhone()));
                partnerVO.setEmail(partner.getEmail());
                vo.setPartner(partnerVO);
            }
        }

        // 7. 统计数据
        UserStatsVO stats = buildUserStats(user);
        vo.setStats(stats);

        // 8. 存入 Redis 缓存（不设过期时间）
        ServiceHelper.putToCache(redisTemplate, objectMapper, cacheKey, vo);

        return Result.success(vo);
    }

    /**
     * 更新用户信息
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<UserVO> updateUserInfo(Integer userId, UserProfileUpdateDTO dto) {
        // 1. 查询用户
        User user = userMapper.selectById(userId);
        if (user == null) {
            return Result.error(ResultCode.NOT_FOUND, "用户不存在");
        }

        // 2. 校验必填字段
        if (!StringUtils.hasText(dto.getNickname())) {
            return Result.badRequest("昵称不能为空");
        }
        if (dto.getNickname().length() > 11) {
            return Result.badRequest("昵称长度需在1-11个字符之间");
        }
        if (!StringUtils.hasText(dto.getPhone())) {
            return Result.badRequest("手机号不能为空");
        }
        if (!StringUtils.hasText(dto.getEmail())) {
            return Result.badRequest("邮箱不能为空");
        }

        // 3. 更新基本信息
        user.setNickname(dto.getNickname());
        user.setPhone(dto.getPhone());
        user.setEmail(dto.getEmail());

        // 4. 处理密码修改（选填）
        if (StringUtils.hasText(dto.getNewPassword())) {
            // 校验当前密码
            if (!StringUtils.hasText(dto.getPassword())) {
                return Result.badRequest("修改密码需要提供当前密码");
            }
            // 校验新密码与确认密码
            if (!dto.getNewPassword().equals(dto.getConfirmPassword())) {
                return Result.badRequest("两次输入的新密码不一致");
            }
            if (dto.getNewPassword().length() < 6) {
                return Result.badRequest("新密码长度不能少于6位");
            }
            // 验证当前密码是否正确
            if (!passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
                return Result.badRequest("当前密码错误");
            }
            // 更新密码
            user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
            log.info("用户密码修改成功, userId:{}", userId);
        }

        // 5. 保存更新
        try {
            userMapper.updateUser(user);
        } catch (DuplicateKeyException e) {
            String message = e.getMessage();
            if (message != null && message.contains("uk_phone")) {
                return Result.badRequest("该手机号已被其他账号绑定，请勿重复绑定");
            }
            if (message != null && message.contains("uk_email")) {
                return Result.badRequest("该邮箱已被其他账号绑定，请勿重复绑定");
            }
            throw e;
        }
        log.info("用户信息更新成功, userId:{}", userId);

        // 6. 清除 Redis 缓存（按 userId 清除，与查询时使用一致）
        redisTemplate.delete(UserConstant.USER_INFO + user.getId());

        // 7. 返回更新后的信息
        return getUserInfo(userId);
    }

    /**
     * 获取用户统计数据
     */
    @Override
    public Result<UserStatsVO> getUserStats(Integer userId) {
        // 2. 查询数据库（先查用户获取 groupId）
        User user = userMapper.selectById(userId);
        if (user == null) {
            return Result.error(ResultCode.NOT_FOUND, "用户不存在");
        }

        // 同上，缓存按 userId 区分，避免情侣间的 stats 缓存互相覆盖
        String cacheKey = UserConstant.USER_STATS + user.getId();

        // 1. 尝试从 Redis 缓存获取
        UserStatsVO cached = ServiceHelper.getFromCache(redisTemplate, objectMapper, cacheKey, UserStatsVO.class);
        if (cached != null) {
            return Result.success(cached);
        }

        UserStatsVO stats = buildUserStats(user);

        // 3. 存入 Redis 缓存，过期时间为当天零点
        try {
            String json = objectMapper.writeValueAsString(stats);
            long secondsUntilMidnight = ChronoUnit.SECONDS.between(LocalDateTime.now(), LocalDate.now().plusDays(1).atStartOfDay());
            redisTemplate.opsForValue().set(cacheKey, json, secondsUntilMidnight, java.util.concurrent.TimeUnit.SECONDS);
        } catch (JsonProcessingException e) {
            log.warn("序列化用户统计数据缓存失败, userId: {}", userId, e);
        }

        return Result.success(stats);
    }

    /**
     * 清除用户统计数据缓存
     * 在照片/相册/天数等发生变化时调用
     */
    public void clearUserStatsCache(Integer userId) {
        User user = userMapper.selectById(userId);
        if (user == null) return;
        String cacheKey = UserConstant.USER_STATS + user.getId();
        redisTemplate.delete(cacheKey);
        log.debug("已清除用户 {} 的统计数据缓存, key: {}", userId, cacheKey);
    }

    /**
     * 构建用户统计数据
     */
    private UserStatsVO buildUserStats(User user) {
        UserStatsVO stats = new UserStatsVO();

        // 在一起的天数
        if (user.getRelationshipStart() != null) {
            long days = ChronoUnit.DAYS.between(user.getRelationshipStart(), LocalDate.now());
            stats.setDaysTogether((int) days);
        } else {
            stats.setDaysTogether(0);
        }

        boolean isBound = user.getIsBound() != null && user.getIsBound() == 1;

        // 已绑定时使用 group_id 查询情侣双方的共同数据，否则仅查询用户自身数据
        if (isBound && user.getGroupId() != null) {
            Integer groupId = user.getGroupId().intValue();
            Integer photoCount = userMapper.countPhotoByGroupId(groupId);
            stats.setPhotoCount(photoCount != null ? photoCount : 0);

            Integer cityCount = userMapper.countCityByGroupId(groupId);
            stats.setCityCount(cityCount != null ? cityCount : 0);

            List<UserStatsVO.ProvinceStatVO> provinces = userMapper.countProvinceByGroupId(groupId);
            log.info(" provinces: {},groupId:{}", provinces, groupId);
            stats.setCities(provinces);
        } else {
            Integer photoCount = userMapper.countPhotoByUserId(user.getId());
            stats.setPhotoCount(photoCount != null ? photoCount : 0);

            Integer cityCount = userMapper.countCityByUserId(user.getId());
            stats.setCityCount(cityCount != null ? cityCount : 0);

            List<UserStatsVO.ProvinceStatVO> provinces = userMapper.countProvinceByUserId(user.getId());
            stats.setCities(provinces);
        }

        // 相册始终通过 group_id 关联查询（未绑定时子查询返回 null，结果为 0）
        Integer albumCount = userMapper.countAlbumByUserId(user.getId());
        stats.setAlbumCount(albumCount != null ? albumCount : 0);

        return stats;
    }

    /**
     * 手机号脱敏：138****8000
     */
    private String maskPhone(String phone) {
        if (!StringUtils.hasText(phone) || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    /**
     * 上传并更新用户头像
     * 1. 将文件上传至阿里云OSS
     * 2. 将OSS访问URL保存到数据库
     * 3. 删除旧头像文件（如有）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<UserVO> updateAvatar(Integer userId, MultipartFile file) {
        // 1. 查询用户
        User user = userMapper.selectById(userId);
        if (user == null) {
            return Result.error(ResultCode.NOT_FOUND, "用户不存在");
        }

        // 2. 删除旧头像（如果有且上传成功后再删除）
        String oldAvatarUrl = user.getAvatarUrl();

        // 3. 上传新头像到OSS
        String newAvatarUrl;
        try {
            newAvatarUrl = aliyunOSSUtils.asyncUploadAvatar(file, userId);
        } catch (Exception e) {
            log.error("头像上传OSS失败, userId: {}", userId, e);
            return Result.error(ResultCode.INTERNAL_SERVER_ERROR, "头像上传失败");
        }

        // 4. 保存新头像URL到数据库
        user.setAvatarUrl(newAvatarUrl);
        userMapper.updateUser(user);
        log.info("用户头像更新成功, userId: {}, avatarUrl: {}", userId, newAvatarUrl);

        // 5. 清除 Redis 缓存
        redisTemplate.delete(UserConstant.USER_INFO + userId);

        // 6. 删除旧头像文件
        if (StringUtils.hasText(oldAvatarUrl)) {
            try {
                String oldObjectKey = ServiceHelper.extractObjectKey(oldAvatarUrl);
                if (oldObjectKey != null) {
                    aliyunOSSUtils.deleteFile(oldObjectKey);
                    log.info("旧头像已删除, userId: {}, objectKey: {}", userId, oldObjectKey);
                }
            } catch (Exception e) {
                log.warn("删除旧头像失败, userId: {}, oldAvatarUrl: {}", userId, oldAvatarUrl, e);
            }
        }

        // 7. 返回更新后的用户信息
        return getUserInfo(userId);
    }

    /**
     * 获取用户通知设置（从Redis读取，不存在则返回默认值）
     */
    @Override
    public Result<NotificationSettingsVO> getNotificationSettings(Integer userId) {
        String cacheKey = UserConstant.USER_NOTIFICATION_SETTINGS + userId;

        // 尝试从缓存获取
        NotificationSettingsVO cached = ServiceHelper.getFromCache(redisTemplate, objectMapper, cacheKey, NotificationSettingsVO.class);
        if (cached != null) {
            return Result.success(cached);
        }

        // 新用户返回默认值（全部开启）
        NotificationSettingsVO defaults = buildDefaultNotificationSettings();
        return Result.success(defaults);
    }

    /**
     * 更新用户通知设置（部分更新，直接写入Redis）
     */
    @Override
    public Result<NotificationSettingsVO> updateNotificationSettings(Integer userId, NotificationSettingsDTO dto) {
        String cacheKey = UserConstant.USER_NOTIFICATION_SETTINGS + userId;

        // 1. 读取当前设置（不存在则用默认值）
        NotificationSettingsVO current = ServiceHelper.getFromCache(redisTemplate, objectMapper, cacheKey, NotificationSettingsVO.class);
        if (current == null) {
            current = buildDefaultNotificationSettings();
        }

        // 2. 部分更新：仅覆盖非null字段
        if (dto.getEnablePush() != null) {
            current.setEnablePush(dto.getEnablePush());
        }
        if (dto.getPhotoUpload() != null) {
            current.setPhotoUpload(dto.getPhotoUpload());
        }
        if (dto.getAnniversary() != null) {
            current.setAnniversary(dto.getAnniversary());
        }
        if (dto.getEmail() != null) {
            current.setEmail(dto.getEmail());
        }
        if (dto.getSystem() != null) {
            current.setSystem(dto.getSystem());
        }

        // 3. 写入Redis
        ServiceHelper.putToCache(redisTemplate, objectMapper, cacheKey, current);

        log.info("用户通知设置已更新, userId: {}", userId);
        return Result.success("设置已保存", current);
    }

    /**
     * 构建默认通知设置
     */
    private NotificationSettingsVO buildDefaultNotificationSettings() {
        NotificationSettingsVO settings = new NotificationSettingsVO();
        settings.setEnablePush(true);
        settings.setPhotoUpload(true);
        settings.setAnniversary(true);
        settings.setEmail(false);
        settings.setSystem(true);
        return settings;
    }
}
