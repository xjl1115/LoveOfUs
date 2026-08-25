package com.example.lovemap.service;

import com.example.lovemap.common.Result;
import com.example.lovemap.model.dto.NotificationSettingsDTO;
import com.example.lovemap.model.dto.UserProfileUpdateDTO;
import com.example.lovemap.model.vo.NotificationSettingsVO;
import com.example.lovemap.model.vo.UserStatsVO;
import com.example.lovemap.model.vo.UserVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 用户服务接口
 */
public interface UserService {

    /**
     * 获取当前用户信息（含伴侣信息、绑定码、统计数据）
     */
    Result<UserVO> getUserInfo(Integer userId);

    /**
     * 更新用户信息
     */
    Result<UserVO> updateUserInfo(Integer userId, UserProfileUpdateDTO dto);

    /**
     * 获取用户统计数据
     */
    Result<UserStatsVO> getUserStats(Integer userId);

    /**
     * 上传并更新用户头像
     *
     * @param userId 用户ID
     * @param file   头像文件
     * @return 更新后的用户信息
     */
    Result<UserVO> updateAvatar(Integer userId, MultipartFile file);

    /**
     * 清除用户统计数据缓存
     * 在照片新增/删除、相册新增/删除、在一起天数变化时由相关服务调用
     */
    void clearUserStatsCache(Integer userId);

    /**
     * 获取用户通知设置
     */
    Result<NotificationSettingsVO> getNotificationSettings(Integer userId);

    /**
     * 更新用户通知设置（部分更新）
     */
    Result<NotificationSettingsVO> updateNotificationSettings(Integer userId, NotificationSettingsDTO dto);
}
