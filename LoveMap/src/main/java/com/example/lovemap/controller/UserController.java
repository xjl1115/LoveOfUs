package com.example.lovemap.controller;

import com.example.lovemap.common.Result;
import com.example.lovemap.model.dto.NotificationSettingsDTO;
import com.example.lovemap.model.dto.UserProfileUpdateDTO;
import com.example.lovemap.model.vo.NotificationSettingsVO;
import com.example.lovemap.model.vo.UserStatsVO;
import com.example.lovemap.model.vo.UserVO;
import com.example.lovemap.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@Slf4j
@Tag(name = "用户管理", description = "用户管理接口")
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/profile")
    @Operation(summary = "获取用户信息")
    public Result<UserVO> getUserInfo(@RequestAttribute("userId") Integer userId) {
        return userService.getUserInfo(userId);
    }

    @PutMapping("/profile")
    @Operation(summary = "更新用户信息")
    public Result<UserVO> updateUserInfo(@RequestAttribute("userId") Integer userId,
                                          @Valid @RequestBody UserProfileUpdateDTO dto) {
        return userService.updateUserInfo(userId, dto);
    }

    @GetMapping("/stats")
    @Operation(summary = "获取用户统计数据")
    public Result<UserStatsVO> getUserStats(@RequestAttribute("userId") Integer userId) {
        return userService.getUserStats(userId);
    }

    @PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "上传头像")
    public Result<UserVO> updateAvatar(@RequestAttribute("userId") Integer userId,
                                        @RequestParam("file") MultipartFile file) {
        return userService.updateAvatar(userId, file);
    }

    @GetMapping("/notification-settings")
    @Operation(summary = "获取通知设置")
    public Result<NotificationSettingsVO> getNotificationSettings(@RequestAttribute("userId") Integer userId) {
        return userService.getNotificationSettings(userId);
    }

    @PutMapping("/notification-settings")
    @Operation(summary = "更新通知设置")
    public Result<NotificationSettingsVO> updateNotificationSettings(
            @RequestAttribute("userId") Integer userId,
            @RequestBody NotificationSettingsDTO dto) {
        return userService.updateNotificationSettings(userId, dto);
    }
}
