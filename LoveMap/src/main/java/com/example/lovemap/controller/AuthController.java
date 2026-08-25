package com.example.lovemap.controller;

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
import com.example.lovemap.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
@Tag(name = "权限管理", description = "认证与授权接口")
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/captcha/send")
    @Operation(summary = "发送邮箱验证码")
    public Result<CaptchaSendResultVO> sendCaptcha(@Valid @RequestBody CaptchaSendDTO captchaSendDTO) {
        return authService.sendCaptcha(captchaSendDTO);
    }

    @PostMapping("/captcha/verify")
    @Operation(summary = "验证验证码")
    public Result<Void> verifyCaptcha(@Valid @RequestBody CaptchaVerifyDTO captchaVerifyDTO) {
        return authService.verifyCaptcha(captchaVerifyDTO);
    }

    @PostMapping("/register")
    @Operation(summary = "用户注册")
    public Result<LoginResultVO> register(@Valid @RequestBody RegisterDTO registerDTO) {
        return authService.register(registerDTO);
    }

    @PostMapping("/login")
    @Operation(summary = "用户登录")
    public Result<LoginResultVO> login(@Valid @RequestBody LoginDTO loginDTO) {
        return authService.login(loginDTO);
    }

    @PostMapping("/logout")
    @Operation(summary = "退出登录")
    public Result<Void> logout(@RequestHeader("Authorization") String authorization) {
        return authService.logout(authorization);
    }

    @PostMapping("/token/refresh")
    @Operation(summary = "刷新Token")
    public Result<LoginResultVO> refreshToken(@RequestHeader("Authorization") String authorization) {
        return authService.refreshToken(authorization);
    }

    @GetMapping("/bind-code")
    @Operation(summary = "获取我的绑定码")
    public Result<BindCodeDTO> getBindCode(@RequestAttribute("userId") Integer userId) {
        return authService.getBindCode(userId);
    }

    @PostMapping("/bind-code/generate")
    @Operation(summary = "生成绑定码")
    public Result<BindCodeDTO> generateBindCode(@RequestAttribute("userId") Integer userId) {
        return authService.generateBindCode(userId);
    }

    @PostMapping("/password/reset")
    @Operation(summary = "重置密码")
    public Result<Void> resetPassword(@Valid @RequestBody PasswordResetDTO dto) {
        return authService.resetPassword(dto);
    }

    @PostMapping("/bind")
    @Operation(summary = "绑定伴侣")
    public Result<Void> bindPartner(@RequestAttribute("userId") Integer userId,
            @Valid @RequestBody BindPartnerDTO dto) {
        return authService.bindPartner(userId, dto);
    }

    @PostMapping("/unbind")
    @Operation(summary = "解除绑定")
    public Result<UnbindStatusVO> unbind(@RequestAttribute("userId") Integer userId) {
        return authService.unbind(userId);
    }

    @PostMapping("/account/delete")
    @Operation(summary = "注销账号")
    public Result<Void> deleteAccount(@RequestAttribute("userId") Integer userId,
            @Valid @RequestBody AccountDeleteDTO dto) {
        return authService.deleteAccount(userId, dto);
    }
}
