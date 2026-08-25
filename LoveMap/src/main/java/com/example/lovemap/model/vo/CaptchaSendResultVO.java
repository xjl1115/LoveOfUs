package com.example.lovemap.model.vo;

import lombok.Data;

/**
 * 发送验证码结果VO
 */
@Data
public class CaptchaSendResultVO {

    /**
     * 验证码过期秒数
     */
    private Integer expireSeconds;

    /**
     * 冷却秒数
     */
    private Integer cooldownSeconds;
}
