package com.example.lovemap.service;

import com.example.lovemap.common.Result;
import com.example.lovemap.model.dto.BindCodeDTO;
import com.example.lovemap.model.dto.BindPartnerDTO;
import com.example.lovemap.model.dto.CaptchaSendDTO;
import com.example.lovemap.model.dto.CaptchaVerifyDTO;
import com.example.lovemap.model.dto.LoginDTO;
import com.example.lovemap.model.dto.AccountDeleteDTO;
import com.example.lovemap.model.dto.PasswordChangeDTO;
import com.example.lovemap.model.dto.PasswordResetDTO;
import com.example.lovemap.model.dto.RegisterDTO;
import com.example.lovemap.model.vo.CaptchaSendResultVO;
import com.example.lovemap.model.vo.LoginResultVO;
import com.example.lovemap.model.vo.UnbindStatusVO;

/**
 * 认证服务接口
 */
public interface AuthService {

    /**
     * 发送验证码
     */
    Result<CaptchaSendResultVO> sendCaptcha(CaptchaSendDTO captchaSendDTO);

    /**
     * 验证验证码
     */
    Result<Void> verifyCaptcha(CaptchaVerifyDTO captchaVerifyDTO);

    /**
     * 用户注册
     */
    Result<LoginResultVO> register(RegisterDTO registerDTO);

    /**
     * 用户登录
     */
    Result<LoginResultVO> login(LoginDTO loginDTO);

    /**
     * 退出登录
     */
    Result<Void> logout(String token);

    /**
     * 刷新 Token
     */
    Result<LoginResultVO> refreshToken(String authorization);

    /**
     * 生成绑定码
     */
    Result<BindCodeDTO> generateBindCode(Integer userId);

    /**
     * 获取我的绑定码
     */
    Result<BindCodeDTO> getBindCode(Integer userId);

    /**
     * 重置密码（通过验证码验证，无需登录）
     *
     * @param dto 重置密码请求
     * @return 操作结果
     */
    Result<Void> resetPassword(PasswordResetDTO dto);

    /**
     * 绑定伴侣
     */
    Result<Void> bindPartner(Integer userId, BindPartnerDTO dto);

    /**
     * 解除绑定
     */
    Result<UnbindStatusVO> unbind(Integer userId);

    /**
     * 注销账号（软删除）
     *
     * @param userId 用户ID
     * @param dto    确认信息（密码）
     * @return 操作结果
     */
    Result<Void> deleteAccount(Integer userId, AccountDeleteDTO dto);
}
