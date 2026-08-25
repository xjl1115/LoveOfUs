package com.example.lovemap.model.vo;

import lombok.Data;

/**
 * 通知设置 VO
 */
@Data
public class NotificationSettingsVO {

    /**
     * 接收新消息通知总开关
     */
    private Boolean enablePush;

    /**
     * 伴侣上传照片提醒
     */
    private Boolean photoUpload;

    /**
     * 纪念日提醒
     */
    private Boolean anniversary;

    /**
     * 邮箱通知
     */
    private Boolean email;

    /**
     * 系统公告
     */
    private Boolean system;
}
