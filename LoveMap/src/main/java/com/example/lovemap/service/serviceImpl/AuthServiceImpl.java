package com.example.lovemap.service.serviceImpl;

import com.example.lovemap.common.Result;
import com.example.lovemap.common.ResultCode;
import com.example.lovemap.common.constant.CaptchaConstant;
import com.example.lovemap.common.constant.UserConstant;
import com.example.lovemap.mapper.GroupMapper;
import com.example.lovemap.mapper.UserMapper;
import com.example.lovemap.model.dto.BindCodeDTO;
import com.example.lovemap.model.dto.BindPartnerDTO;
import com.example.lovemap.model.dto.CaptchaSendDTO;
import com.example.lovemap.model.dto.CaptchaVerifyDTO;
import com.example.lovemap.model.dto.LoginDTO;
import com.example.lovemap.model.dto.AccountDeleteDTO;
import com.example.lovemap.model.dto.PasswordChangeDTO;
import com.example.lovemap.model.dto.PasswordResetDTO;
import com.example.lovemap.model.dto.RegisterDTO;
import com.example.lovemap.model.entity.Group;
import com.example.lovemap.model.entity.User;
import com.example.lovemap.model.vo.CaptchaSendResultVO;
import com.example.lovemap.model.vo.LoginResultVO;
import com.example.lovemap.model.vo.UnbindStatusVO;
import com.example.lovemap.service.AsyncMailService;
import com.example.lovemap.service.AuthService;
import com.example.lovemap.service.RateLimiterService;
import com.example.lovemap.utils.JwtUtils;
import com.example.lovemap.utils.RandomCaptcha;
import com.example.lovemap.utils.TokenUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 认证服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final AsyncMailService asyncMailService;
    private final RateLimiterService rateLimiterService;
    private final RandomCaptcha randomCaptcha;
    private final StringRedisTemplate redisTemplate;
    private final UserMapper userMapper;
    private final GroupMapper groupMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final TokenUtils tokenUtils;

    /**
     * 验证码过期时间（秒），从配置文件注入
     */
    @Value("${verify.code.expire}")
    private int captchaExpireSeconds;

    /**
     * 验证码长度，从配置文件注入
     */
    @Value("${verify.code.length}")
    private int captchaLength;

    /**
     * 发送验证码
     */
    @Override
    public Result<CaptchaSendResultVO> sendCaptcha(CaptchaSendDTO dto) {
        String target = dto.getTarget();
        String channel = dto.getChannel();
        String type = dto.getType();

        // 参数校验
        if (!StringUtils.hasText(target)) {
            return Result.badRequest("手机号或邮箱不能为空");
        }
        if (!StringUtils.hasText(channel)) {
            return Result.badRequest("发送渠道不能为空");
        }
        if (!StringUtils.hasText(type)) {
            return Result.badRequest("验证码类型不能为空");
        }

        // 渠道校验
        if ("email".equalsIgnoreCase(channel)) {
            if (!isValidEmail(target)) {
                return Result.badRequest("邮箱格式不正确");
            }
        } else if ("sms".equalsIgnoreCase(channel)) {
            if (!isValidPhone(target)) {
                return Result.badRequest("手机号格式不正确");
            }
        } else {
            return Result.badRequest("不支持的发送渠道：" + channel);
        }

        // 1. 限流检查（滑动窗口，10分钟内最多5次）
        boolean allowed = rateLimiterService.checkAndRecord(target);
        if (!allowed) {
            long remainingSeconds = rateLimiterService.getWindowSeconds();
            return Result.error(ResultCode.CAPTCHA_TOO_FREQUENT,
                    "发送过于频繁，请" + remainingSeconds + "秒后再试");
        }

        // 2. 检查是否有未过期的验证码（防止重复发送）
        String captchaKey = buildCaptchaKey(target, channel, type);
        Boolean hasKey = redisTemplate.hasKey(captchaKey);
        if (Boolean.TRUE.equals(hasKey)) {
            Long ttl = redisTemplate.getExpire(captchaKey, TimeUnit.SECONDS);
            if (ttl != null && ttl > 0) {
                return Result.error(ResultCode.CAPTCHA_TOO_FREQUENT,
                        "验证码已发送，请" + ttl + "秒后再试");
            }
        }

        // 3. 生成验证码
        String captchaCode = randomCaptcha.generateDigitCode(captchaLength);

        // 4. 存入 Redis
        redisTemplate.opsForValue().set(captchaKey, captchaCode, captchaExpireSeconds, TimeUnit.SECONDS);

        // 5. 根据渠道发送验证码
        if ("email".equalsIgnoreCase(channel)) {
            asyncMailService.sendVerifyCodeMailAsync(target, captchaCode, type, captchaExpireSeconds);
            log.info("邮箱验证码发送成功, 邮箱:{}, 类型:{}", target, type);
        } else {
            log.info("短信验证码发送(模拟), 手机号:{}, 验证码:{}, 类型:{}", target, captchaCode, type);
        }

        // 6. 返回结果
        CaptchaSendResultVO resultVO = new CaptchaSendResultVO();
        resultVO.setExpireSeconds(captchaExpireSeconds);
        resultVO.setCooldownSeconds(captchaExpireSeconds);
        return Result.success("验证码发送成功", resultVO);
    }

    /**
     * 验证验证码
     */
    @Override
    public Result<Void> verifyCaptcha(CaptchaVerifyDTO dto) {
        String target = dto.getTarget();
        String channel = dto.getChannel();
        String code = dto.getCaptcha();
        String type = dto.getType();

        if (!StringUtils.hasText(target) || !StringUtils.hasText(code) || !StringUtils.hasText(type)) {
            return Result.badRequest("参数不完整");
        }

        String captchaKey = buildCaptchaKey(target, channel, type);
        String storedCode = redisTemplate.opsForValue().get(captchaKey);

        if (storedCode == null) {
            return Result.error(ResultCode.CAPTCHA_EXPIRED);
        }

        if (!storedCode.equals(code.trim())) {
            return Result.error(ResultCode.CAPTCHA_ERROR);
        }

        // 验证成功后删除验证码（一次性使用）
        redisTemplate.delete(captchaKey);

        log.info("验证码验证成功, target:{}, channel:{}, type:{}", target, channel, type);
        return Result.success("验证成功", null);
    }

    /**
     * 注册
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<LoginResultVO> register(RegisterDTO dto) {
        String email = dto.getEmail();
        String password = dto.getPassword();
        String confirmPassword = dto.getConfirmPassword();
        String captcha = dto.getCaptcha();
        String nickname = dto.getNickname();

        // 1. 校验验证码（优先验证，避免无效请求查库）
        Result<LoginResultVO> captchaResult = verifyCaptchaCode(email, "email", "register", captcha);
        if (captchaResult != null) {
            return captchaResult;
        }

        // 2. 校验密码一致性
        if (!password.equals(confirmPassword)) {
            return Result.error(ResultCode.UNPROCESSABLE_ENTITY, "两次输入的密码不一致");
        }

        // 3. 校验邮箱是否已被注册
        User existingUser = userMapper.findByEmail(email);
        if (existingUser != null) {
            return Result.error(ResultCode.CONFLICT, "该邮箱已被注册");
        }

        // 4. 密码加密
        String encodedPassword = passwordEncoder.encode(password);

        // 5. 创建用户
        User user = new User();
        user.setNickname(nickname);
        user.setAvatarUrl(UserConstant.DEFAULT_AVATAR_URL);
        user.setEmail(email);
        user.setPhone(dto.getPhone());
        user.setPassword(encodedPassword);
        user.setIsBound(0);

        userMapper.insert(user);

        // 6. 自动绑定伴侣（如果提供了绑定码）
        boolean bound = false;
        String partnerCode = dto.getPartnerCode();
        if (StringUtils.hasText(partnerCode)) {
            Result<Void> bindResult = doBindPartner(user.getId().intValue(), partnerCode);
            if (bindResult.getCode() != 200) {
                log.warn("注册时自动绑定伴侣失败, userId:{}, partnerCode:{}, reason:{}",
                        user.getId(), partnerCode, bindResult.getMessage());
                return Result.error(bindResult.getCode(), "绑定伴侣失败：" + bindResult.getMessage());
            }
            bound = true;
            log.info("注册时自动绑定伴侣成功, userId:{}, partnerCode:{}", user.getId(), partnerCode);
        }

        // 7. 生成 JWT Token
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId());
        claims.put("nickName", nickname);
        String token = jwtUtils.createAccessToken(claims);
        String refreshToken = jwtUtils.createRefreshToken(claims);

        // 8. 返回注册结果
        LoginResultVO.UserInfoVO userInfo = new LoginResultVO.UserInfoVO();
        userInfo.setNickname(nickname);
        userInfo.setAvatarUrl(null);
        userInfo.setIsBound(bound);

        LoginResultVO resultVO = new LoginResultVO();
        resultVO.setUserId(user.getId());
        resultVO.setToken(token);
        resultVO.setRefreshToken(refreshToken);
        resultVO.setExpiresIn(jwtUtils.getExpiration() / 1000);
        resultVO.setUserInfo(userInfo);

        return Result.success(bound ? "注册并绑定成功" : "注册成功", resultVO);
    }

    /**
     * 登录
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<LoginResultVO> login(LoginDTO loginDTO) {
        String account = loginDTO.getAccount();
        String loginType = loginDTO.getLoginType();

        // 1. 参数校验
        if (!StringUtils.hasText(account)) {
            return Result.badRequest("账号不能为空");
        }
        if (!StringUtils.hasText(loginType)) {
            return Result.badRequest("登录方式不能为空");
        }
        if (!"password".equals(loginType) && !"captcha".equals(loginType)) {
            return Result.badRequest("不支持的登录方式：" + loginType);
        }

        // 2. 检查账号是否被锁定
        if (isAccountLocked(account)) {
            long remainingSeconds = getLockRemainingSeconds(account);
            log.warn("账号已被锁定，尝试登录, account:{}, remainingSeconds:{}", account, remainingSeconds);
            return Result.error(ResultCode.FORBIDDEN, "账号已被锁定，请" + remainingSeconds + "秒后再试");
        }

        boolean isEmail = account.contains("@");

        // ==================== 密码登录 ====================
        if ("password".equals(loginType)) {
            String password = loginDTO.getPassword();
            if (!StringUtils.hasText(password)) {
                return Result.badRequest("密码不能为空");
            }

            // 3. 先验证该手机号或邮箱是否注册过，并检查重复登录
            User user = findUserByAccount(account, isEmail);
            Result<LoginResultVO> validationResult = validateUserLogin(user, account);
            if (validationResult != null) {
                return validationResult;
            }

            // 4. 验证密码（加密后）是否相同
            boolean matches = passwordEncoder.matches(password, user.getPassword());
            if (!matches) {
                // 记录登录失败
                recordLoginFailure(account);
                log.warn("密码登录失败, account:{}, 密码错误", account);
                return Result.error(ResultCode.UNAUTHORIZED, "账号或密码错误");
            }

            // 5. 登录成功后，清除失败记录
            clearLoginFailures(account);

            // 6. 将用户id存入Redis，过期时间设置为用户token过期时间
            saveLoginSession(user);

            return buildLoginResult(user, account, loginType);
        }

        // ==================== 验证码登录 ====================
        // 3. 先验证验证码是否相同
        String captcha = loginDTO.getCaptcha();
        if (!StringUtils.hasText(captcha)) {
            return Result.badRequest("验证码不能为空");
        }
        String channel = isEmail ? "email" : "sms";
        Result<LoginResultVO> captchaResult = verifyCaptchaCode(account, channel, "login", captcha);
        if (captchaResult != null) {
            return captchaResult;
        }

        // 4. 验证该手机号或邮箱是否注册过，并检查重复登录
        User user = findUserByAccount(account, isEmail);
        Result<LoginResultVO> validationResult = validateUserLogin(user, account);
        if (validationResult != null) {
            return validationResult;
        }

        // 5. 登录成功后，清除失败记录
        clearLoginFailures(account);

        // 6. 将用户id存入Redis，过期时间设置为用户token过期时间
        saveLoginSession(user);

        log.info("验证码登录成功, userId:{}, account:{}", user.getId(), account);
        return buildLoginResult(user, account, loginType);
    }

    /**
     * 退出登录
     */
    @Override
    public Result<Void> logout(String authorization) {
        // 1. 提取纯 Token
        String pureToken = tokenUtils.extractToken(authorization);

        // 2. 从 Token 中获取用户 ID
        Integer userId = tokenUtils.getUserId(pureToken);
        if (userId == null) {
            return Result.error(ResultCode.UNAUTHORIZED, "Token 无效");
        }

        // 3. 清理 Redis 中该用户的登录 Session
        User user = userMapper.selectById(userId);
        String sessionKey = CaptchaConstant.USER_LOGIN_SESSION_PREFIX + (user != null ? user.getNickname() : userId);
        redisTemplate.delete(sessionKey);

        // 4. 将 Token 加入 Redis 黑名单，黑名单 TTL 设置为 Token 剩余有效期
        Long remainingTime = jwtUtils.getRemainingTime(pureToken);
        if (remainingTime != null && remainingTime > 0) {
            String blacklistKey = UserConstant.TOKEN_BLACKLIST + pureToken;
            redisTemplate.opsForValue().set(
                    blacklistKey,
                    String.valueOf(userId),
                    remainingTime,
                    TimeUnit.MILLISECONDS
            );
            log.info("退出登录 - Token已加入黑名单, userId:{}, remainingTime:{}ms", userId, remainingTime);
        }

        return Result.success("退出登录成功", null);
    }

    /**
     * 刷新 Token（无感刷新）
     */
    @Override
    public Result<LoginResultVO> refreshToken(String authorization) {
        // 1. 提取纯 Token
        String pureToken = tokenUtils.extractToken(authorization);

        // 2. 验证 Token 是否在黑名单中（已主动登出的 Token 不可刷新）
        if (tokenUtils.isTokenBlacklisted(pureToken)) {
            return Result.error(ResultCode.UNAUTHORIZED, "Token 已失效，请重新登录");
        }

        // 3. 从 Token 中获取用户信息（支持过期 Token 刷新）
        Integer userId;
        try {
            io.jsonwebtoken.Claims claims = jwtUtils.parseJWTWithoutExpiration(pureToken);
            Object userIdObj = claims.get(TokenUtils.CLAIM_USER_ID);
            if (userIdObj instanceof Number) {
                userId = ((Number) userIdObj).intValue();
            } else {
                return Result.error(ResultCode.UNAUTHORIZED, "Token 无效");
            }
        } catch (Exception e) {
            log.warn("Token 解析失败: {}", e.getMessage());
            return Result.error(ResultCode.UNAUTHORIZED, "Token 无效");
        }

        // 4. 刷新 Token（JwtUtils 支持解析已过期 Token 的 claims 重新生成）
        String newToken = jwtUtils.refreshToken(pureToken);
        if (newToken == null) {
            return Result.error(ResultCode.UNAUTHORIZED, "Token 刷新失败，请重新登录");
        }

        // 5. 将旧 Token 加入黑名单，TTL 设为原剩余有效期（防止 Token 泄露后继续使用）
        Long remainingTime = jwtUtils.getRemainingTime(pureToken);
        if (remainingTime != null && remainingTime > 0) {
            String blacklistKey = UserConstant.TOKEN_BLACKLIST + pureToken;
            redisTemplate.opsForValue().set(
                    blacklistKey,
                    String.valueOf(userId),
                    remainingTime,
                    TimeUnit.MILLISECONDS
            );
        }

        // 6. 更新 Redis 中登录 Session 的 TTL（随 Token 刷新延长）
        User user = userMapper.selectById(userId);
        String sessionKey = CaptchaConstant.USER_LOGIN_SESSION_PREFIX + (user != null ? user.getNickname() : userId);
        long tokenExpireSeconds = jwtUtils.getExpiration() / 1000;
        redisTemplate.opsForValue().set(sessionKey, String.valueOf(userId), tokenExpireSeconds, TimeUnit.SECONDS);

        // 7. 返回新的 Token 信息
        LoginResultVO resultVO = new LoginResultVO();
        resultVO.setUserId(Long.valueOf(userId));
        resultVO.setToken(newToken);
        resultVO.setRefreshToken(newToken);
        resultVO.setExpiresIn(jwtUtils.getExpiration() / 1000);

        log.info("Token刷新成功, userId:{}", userId);
        return Result.success("Token刷新成功", resultVO);
    }

    /**
     * 重置密码（通过验证码验证，无需登录）
     */
    @Override
    public Result<Void> resetPassword(PasswordResetDTO dto) {
        String target = dto.getTarget();
        String channel = dto.getChannel();
        String captcha = dto.getCaptcha();
        String newPassword = dto.getNewPassword();
        String confirmPassword = dto.getConfirmPassword();

        // 1. 参数校验
        if (!StringUtils.hasText(target)) {
            return Result.badRequest("手机号或邮箱不能为空");
        }
        if (!StringUtils.hasText(channel)) {
            return Result.badRequest("发送渠道不能为空");
        }
        if ("email".equalsIgnoreCase(channel)) {
            if (!isValidEmail(target)) {
                return Result.badRequest("邮箱格式不正确");
            }
        } else if ("sms".equalsIgnoreCase(channel)) {
            if (!isValidPhone(target)) {
                return Result.badRequest("手机号格式不正确");
            }
        } else {
            return Result.badRequest("不支持的发送渠道：" + channel);
        }
        if (!StringUtils.hasText(captcha)) {
            return Result.badRequest("验证码不能为空");
        }
        if (!StringUtils.hasText(newPassword) || !StringUtils.hasText(confirmPassword)) {
            return Result.badRequest("密码不能为空");
        }

        // 2. 校验密码一致性
        if (!newPassword.equals(confirmPassword)) {
            return Result.error(ResultCode.UNPROCESSABLE_ENTITY, "两次输入的密码不一致");
        }

        // 3. 校验密码长度（6-20位）
        if (newPassword.length() < 6 || newPassword.length() > 20) {
            return Result.error(ResultCode.UNPROCESSABLE_ENTITY, "密码长度需在6-20位之间");
        }

        // 4. 校验验证码
        String captchaKey = buildCaptchaKey(target, channel, "reset_password");
        String storedCode = redisTemplate.opsForValue().get(captchaKey);

        if (storedCode == null) {
            return Result.error(ResultCode.CAPTCHA_EXPIRED, "验证码已过期，请重新发送");
        }
        if (!storedCode.equals(captcha.trim())) {
            return Result.error(ResultCode.CAPTCHA_ERROR, "验证码错误");
        }

        // 5. 验证码使用后立即删除（一次性使用）
        redisTemplate.delete(captchaKey);

        // 6. 根据渠道查找用户
        boolean isEmail = "email".equalsIgnoreCase(channel);
        User user = findUserByAccount(target, isEmail);
        if (user == null) {
            String msg = isEmail ? "该邮箱未注册" : "该手机号未注册";
            return Result.error(ResultCode.NOT_FOUND, msg);
        }

        // 7. 更新密码
        String encodedPassword = passwordEncoder.encode(newPassword);
        user.setPassword(encodedPassword);
        userMapper.updatePassword(user);

        // 8. 清除该用户的登录 Session，强制重新登录
        String sessionKey = CaptchaConstant.USER_LOGIN_SESSION_PREFIX + user.getNickname();
        redisTemplate.delete(sessionKey);

        log.info("密码重置成功, userId:{}, target:{}, channel:{}", user.getId(), target, channel);
        return Result.success("密码重置成功", null);
    }


    /**
     * 生成绑定码
     */
    @Override
    public Result<BindCodeDTO> generateBindCode(Integer userId) {
        // 1. 查询用户是否已绑定
        User user = userMapper.selectById(userId);
        if (user == null) {
            return Result.error(ResultCode.NOT_FOUND, "用户不存在");
        }
        if (user.getIsBound() != null && user.getIsBound() == 1) {
            return Result.error(ResultCode.CONFLICT, "用户已绑定伴侣，无法生成绑定码");
        }

        // 2. 检查 Redis 中是否已有绑定码
        String redisKey = CaptchaConstant.BIND_CODE_PREFIX + userId;
        String existingCode = redisTemplate.opsForValue().get(redisKey);
        if (existingCode != null) {
            BindCodeDTO dto = new BindCodeDTO();
            dto.setBindCode(existingCode);
            dto.setIsBound(false);
            log.info("返回已有绑定码, userId:{}, bindCode:{}", userId, existingCode);
            return Result.success("获取绑定码成功", dto);
        }

        // 3. 生成新的绑定码（8位字母数字组合）
        String bindCode = randomCaptcha.generateRandomCode(6);

        // 4. 存入 Redis，不设置过期时间
        redisTemplate.opsForValue().set(redisKey, bindCode);

        log.info("生成绑定码成功, userId:{}, bindCode:{}", userId, bindCode);

        BindCodeDTO dto = new BindCodeDTO();
        dto.setBindCode(bindCode);
        dto.setIsBound(false);
        return Result.success("绑定码生成成功", dto);
    }

    /**
     * 获取我的绑定码
     */
    @Override
    public Result<BindCodeDTO> getBindCode(Integer userId) {
        // 1. 查询用户绑定状态
        User user = userMapper.selectById(userId);
        if (user == null) {
            return Result.error(ResultCode.NOT_FOUND, "用户不存在");
        }

        // 2. 从 Redis 获取绑定码
        String redisKey = CaptchaConstant.BIND_CODE_PREFIX + userId;
        String bindCode = redisTemplate.opsForValue().get(redisKey);

        BindCodeDTO dto = new BindCodeDTO();
        dto.setBindCode(bindCode);
        dto.setIsBound(user.getIsBound() != null && user.getIsBound() == 1);

        return Result.success(dto);
    }

    /**
     * 绑定伴侣
     * 1. 校验绑定码是否有效（从 Redis 中查询）
     * 2. 校验双方是否都未绑定
     * 3. 生成情侣 ID（UUID）并更新双方用户
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> bindPartner(Integer userId, BindPartnerDTO dto) {
        return doBindPartner(userId, dto.getPartnerCode());
    }

    /**
     * 核心绑定逻辑（私有方法，供 bindPartner 和 register 共用）
     */
    private Result<Void> doBindPartner(Integer userId, String partnerCode) {
        if (!StringUtils.hasText(partnerCode)) {
            return Result.badRequest("绑定码不能为空");
        }

        // 1. 查询当前用户
        User currentUser = userMapper.selectById(userId);
        if (currentUser == null) {
            return Result.error(ResultCode.NOT_FOUND, "当前用户不存在");
        }
        if (currentUser.getIsBound() != null && currentUser.getIsBound() == 1) {
            return Result.error(ResultCode.CONFLICT, "当前用户已绑定伴侣，无法再次绑定");
        }

        // 2. 遍历 Redis 找到持有该绑定码的用户
        Long partnerId = null;
        String pattern = CaptchaConstant.BIND_CODE_PREFIX + "*";
        for (String key : redisTemplate.keys(pattern)) {
            String code = redisTemplate.opsForValue().get(key);
            if (partnerCode.equals(code)) {
                String keyPartnerId = key.substring(CaptchaConstant.BIND_CODE_PREFIX.length());
                partnerId = Long.parseLong(keyPartnerId);
                break;
            }
        }

        if (partnerId == null) {
            return Result.error(ResultCode.NOT_FOUND, "绑定码不存在或已过期");
        }

        // 3. 不能绑定自己
        if (partnerId.equals(currentUser.getId())) {
            return Result.error(ResultCode.BAD_REQUEST, "不能绑定自己");
        }

        // 4. 查询伴侣用户
        User partnerUser = userMapper.selectById(partnerId.intValue());
        if (partnerUser == null) {
            return Result.error(ResultCode.NOT_FOUND, "伴侣用户不存在");
        }
        if (partnerUser.getIsBound() != null && partnerUser.getIsBound() == 1) {
            return Result.error(ResultCode.CONFLICT, "对方已绑定伴侣，无法绑定");
        }

        // 5. 生成情侣 UUID 并插入 group 表
        String groupUuid = UUID.randomUUID().toString().replace("-", "");
        Group group = new Group();
        group.setGroupId(groupUuid);
        group.setUser1Id(userId);
        group.setUser2Id(partnerId.intValue());
        groupMapper.insert(group);

        // 6. 使用 group 表自增 id 更新双方 user 表的 group_id
        Integer groupTableId = group.getId();
        userMapper.updatePartnerBind(currentUser.getId(), partnerUser.getId(), groupTableId.longValue());
        userMapper.updatePartnerBind(partnerUser.getId(), currentUser.getId(), groupTableId.longValue());

        // 7. 删除双方的绑定码
        redisTemplate.delete(CaptchaConstant.BIND_CODE_PREFIX + userId);
        redisTemplate.delete(CaptchaConstant.BIND_CODE_PREFIX + partnerId);

        // 8. 清除双方的用户信息缓存
        redisTemplate.delete(UserConstant.USER_INFO + userId);
        redisTemplate.delete(UserConstant.USER_INFO + partnerId);

        log.info("伴侣绑定成功, userId:{}, partnerId:{}, groupTableId:{}", userId, partnerId, groupTableId);
        return Result.success("绑定成功", null);
    }

    /**
     * 验证用户是否存在且未重复登录
     */
    private Result<LoginResultVO> validateUserLogin(User user, String account) {
        if (user == null) {
            return Result.error(ResultCode.NOT_FOUND, "账号不存在");
        }
        String sessionKey = CaptchaConstant.USER_LOGIN_SESSION_PREFIX + user.getNickname();
        Boolean isLoggedIn = redisTemplate.hasKey(sessionKey);
        if (Boolean.TRUE.equals(isLoggedIn)) {
            log.warn("用户重复登录, userId:{}, account:{}", user.getId(), account);
            return Result.error(ResultCode.CONFLICT, "用户已登录，请勿重复登录");
        }
        return null;
    }

    /**
     * 解除伴侣绑定
     * 1. 校验用户是否存在且已绑定
     * 2. 获取伴侣用户并清空双方的伴侣关系
     * 3. 清除 Redis 缓存
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<UnbindStatusVO> unbind(Integer userId) {
        // 1. 查询当前用户
        User currentUser = userMapper.selectById(userId);
        if (currentUser == null) {
            return Result.error(ResultCode.NOT_FOUND, "用户不存在");
        }
        if (currentUser.getIsBound() == null || currentUser.getIsBound() != 1) {
            return Result.error(ResultCode.BAD_REQUEST, "当前用户未绑定伴侣");
        }

        // 2. 获取伴侣用户 ID
        Long partnerId = currentUser.getPartnerId();
        if (partnerId == null) {
            return Result.error(ResultCode.BAD_REQUEST, "伴侣信息异常");
        }

        // 3. 清空当前用户的伴侣关系
        userMapper.clearPartnerBind(currentUser.getId());

        // 4. 清空伴侣用户的伴侣关系
        userMapper.clearPartnerBind(partnerId);

        // 5. 清除双方的 Redis 缓存
        redisTemplate.delete(UserConstant.USER_INFO + userId);
        redisTemplate.delete(UserConstant.USER_INFO + partnerId);

        log.info("伴侣解除绑定成功, userId:{}, exPartnerId:{}", userId, partnerId);

        UnbindStatusVO unbindStatus = new UnbindStatusVO();
        unbindStatus.setRequesting(false);
        unbindStatus.setEffectiveDate(null);
        unbindStatus.setCooldownDays(0);
        return Result.success("解除绑定成功", unbindStatus);
    }

    /**
     * 保存用户登录 Session 到 Redis，过期时间与 Token 一致
     */
    private void saveLoginSession(User user) {
        String sessionKey = CaptchaConstant.USER_LOGIN_SESSION_PREFIX + user.getNickname();
        long tokenExpireSeconds = jwtUtils.getExpiration() / 1000;
        redisTemplate.opsForValue().set(sessionKey, String.valueOf(user.getId()), tokenExpireSeconds, TimeUnit.SECONDS);
    }

    /**
     * 检查账号是否被锁定
     */
    private boolean isAccountLocked(String account) {
        String lockKey = CaptchaConstant.LOGIN_LOCK_PREFIX + account;
        return Boolean.TRUE.equals(redisTemplate.hasKey(lockKey));
    }

    /**
     * 获取账号锁定剩余时间（秒）
     */
    private long getLockRemainingSeconds(String account) {
        String lockKey = CaptchaConstant.LOGIN_LOCK_PREFIX + account;
        Long ttl = redisTemplate.getExpire(lockKey, TimeUnit.SECONDS);
        return ttl != null && ttl > 0 ? ttl : 0;
    }

    /**
     * 记录登录失败次数，达到上限时锁定账号
     */
    private void recordLoginFailure(String account) {
        String failKey = CaptchaConstant.LOGIN_FAIL_PREFIX + account;
        Long count = redisTemplate.opsForValue().increment(failKey);
        if (count != null && count == 1) {
            // 第一次失败，设置过期时间为锁定时间的2倍，确保不会在锁定前过期
            redisTemplate.expire(failKey, CaptchaConstant.LOCK_DURATION_MINUTES * 2, TimeUnit.MINUTES);
        }
        if (count != null && count >= CaptchaConstant.MAX_LOGIN_ATTEMPTS) {
            // 达到上限，锁定账号
            String lockKey = CaptchaConstant.LOGIN_LOCK_PREFIX + account;
            redisTemplate.opsForValue().set(lockKey, "1", CaptchaConstant.LOCK_DURATION_MINUTES, TimeUnit.MINUTES);
            log.warn("账号登录失败达到{}次，已锁定{}分钟, account:{}", count, CaptchaConstant.LOCK_DURATION_MINUTES, account);
        }
    }

    /**
     * 登录成功后清除失败记录
     */
    private void clearLoginFailures(String account) {
        redisTemplate.delete(CaptchaConstant.LOGIN_FAIL_PREFIX + account);
        redisTemplate.delete(CaptchaConstant.LOGIN_LOCK_PREFIX + account);
    }

    /**
     * 验证验证码并立即删除（一次性使用）
     */
    private Result<LoginResultVO> verifyCaptchaCode(String target, String channel, String type, String captchaCode) {
        String captchaKey = buildCaptchaKey(target, channel, type);
        String storedCode = redisTemplate.opsForValue().get(captchaKey);

        if (storedCode == null) {
            return Result.error(ResultCode.CAPTCHA_EXPIRED, "验证码已过期，请重新发送");
        }
        if (!storedCode.equals(captchaCode.trim())) {
            return Result.error(ResultCode.CAPTCHA_ERROR, "验证码错误");
        }
        redisTemplate.delete(captchaKey);
        return null;
    }

    /**
     * 根据账号（邮箱或手机号）查找用户
     */
    private User findUserByAccount(String account, boolean isEmail) {
        if (isEmail) {
            return userMapper.findByEmail(account);
        } else {
            return userMapper.findByPhone(account);
        }
    }

    /**
     * 构建登录成功响应
     */
    private Result<LoginResultVO> buildLoginResult(User user, String account, String loginType) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId());
        claims.put("nickName", user.getNickname());
        String token = jwtUtils.createAccessToken(claims);
        String refreshToken = jwtUtils.createRefreshToken(claims);

        LoginResultVO resultVO = getLoginResultVO(user, token, refreshToken);
        return Result.success("登录成功", resultVO);
    }

    /**
     * 获取登录成功响应数据
     */
    private LoginResultVO getLoginResultVO(User user, String token, String refreshToken) {
        LoginResultVO.UserInfoVO userInfo = new LoginResultVO.UserInfoVO();
        userInfo.setNickname(user.getNickname());
        userInfo.setAvatarUrl(user.getAvatarUrl());
        userInfo.setIsBound(user.getIsBound() != null && user.getIsBound() == 1);

        LoginResultVO resultVO = new LoginResultVO();
        resultVO.setUserId(user.getId());
        resultVO.setToken(token);
        resultVO.setRefreshToken(refreshToken);
        resultVO.setExpiresIn(jwtUtils.getExpiration() / 1000);
        resultVO.setUserInfo(userInfo);
        return resultVO;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> deleteAccount(Integer userId, AccountDeleteDTO dto) {
        // 1. 查询用户
        User user = userMapper.selectById(userId);
        if (user == null) {
            return Result.notFound("用户不存在");
        }

        // 2. 校验密码
        if (!passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            return Result.error(ResultCode.UNAUTHORIZED, "密码错误");
        }

        // 3. 如果已绑定伴侣，自动解除伴侣绑定
        if (user.getIsBound() != null && user.getIsBound() == 1 && user.getPartnerId() != null) {
            User partner = userMapper.selectById(Math.toIntExact(user.getPartnerId()));
            if (partner != null) {
                // 清除伴侣绑定关系
                userMapper.clearPartnerBind(user.getPartnerId());
                // 清除伴侣的登录 Session
                String partnerSessionKey = CaptchaConstant.USER_LOGIN_SESSION_PREFIX + partner.getNickname();
                redisTemplate.delete(partnerSessionKey);
                log.info("注销账号自动解除伴侣绑定, userId:{}, partnerId:{}", userId, partner.getId());
            }
        }

        // 4. 清除自己的登录 Session
        String sessionKey = CaptchaConstant.USER_LOGIN_SESSION_PREFIX + user.getNickname();
        redisTemplate.delete(sessionKey);

        // 5. 清除所有缓存的 Token 黑名单（用户已注销无需黑名单）
        // 6. 软删除用户（清空敏感信息）
        // 昵称保留为 "已注销用户" 以便历史数据可读
        String deletedNickname = "已注销用户_" + user.getId();
        user.setNickname(deletedNickname);
        userMapper.deleteAccount(user);

        log.info("用户注销成功, userId:{}", userId);
        return Result.success("账号已注销", null);
    }

    /**
     * 构建 Redis 存储 KEY
     * 格式：captcha:code:{channel}:{type}:{target}
     */
    private String buildCaptchaKey(String target, String channel, String type) {
        return CaptchaConstant.CAPTCHA_KEY_PREFIX + channel + ":" + type + ":" + target;
    }

    /**
     * 简单邮箱格式校验
     */
    private boolean isValidEmail(String email) {
        return email != null && email.matches("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$");
    }

    /**
     * 简单手机号格式校验（中国大陆11位手机号）
     */
    private boolean isValidPhone(String phone) {
        return phone != null && phone.matches("^1[3-9]\\d{9}$");
    }
}
