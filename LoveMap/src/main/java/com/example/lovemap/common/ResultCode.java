package com.example.lovemap.common;

import lombok.Getter;

/**
 * 响应码枚举
 */
@Getter
public enum ResultCode {

    /**
     * 成功
     */
    SUCCESS(200, "success"),

    /**
     * 请求参数错误
     */
    BAD_REQUEST(400, "请求参数错误"),

    /**
     * 未认证/Token过期
     */
    UNAUTHORIZED(401, "未认证或Token已过期"),

    /**
     * 无权限
     */
    FORBIDDEN(403, "无权限访问"),

    /**
     * 资源不存在
     */
    NOT_FOUND(404, "资源不存在"),

    /**
     * 资源冲突（如重复绑定）
     */
    CONFLICT(409, "资源冲突"),

    /**
     * 文件过大
     */
    PAYLOAD_TOO_LARGE(413, "文件过大"),

    /**
     * 验证失败
     */
    UNPROCESSABLE_ENTITY(422, "验证失败"),

    /**
     * 请求过于频繁
     */
    TOO_MANY_REQUESTS(429, "请求过于频繁"),

    /**
     * 服务不可用
     */
    SERVICE_UNAVAILABLE(503, "服务暂不可用"),

    /**
     * 验证码错误
     */
    CAPTCHA_ERROR(460, "验证码错误"),

    /**
     * 验证码已过期
     */
    CAPTCHA_EXPIRED(461, "验证码已过期"),

    /**
     * 验证码发送过于频繁
     */
    CAPTCHA_TOO_FREQUENT(462, "验证码发送过于频繁"),

    /**
     * 服务器内部错误
     */
    INTERNAL_SERVER_ERROR(500, "服务器内部错误");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

}
