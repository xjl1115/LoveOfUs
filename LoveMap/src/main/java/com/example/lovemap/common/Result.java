package com.example.lovemap.common;

import lombok.Data;

import java.time.Instant;

/**
 * 统一响应体
 *
 * @param <T> 数据类型
 */
@Data
public class Result<T> {

    /**
     * 状态码
     */
    private int code;

    /**
     * 提示信息
     */
    private String message;

    /**
     * 数据
     */
    private T data;

    /**
     * 时间戳
     */
    private long timestamp;

    public Result() {
        this.timestamp = Instant.now().getEpochSecond();
    }

    public Result(int code, String message, T data) {
        this();
        this.code = code;
        this.message = message;
        this.data = data;
    }

    /**
     * 成功响应
     */
    public static <T> Result<T> success() {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), null);
    }

    /**
     * 成功响应（带数据）
     */
    public static <T> Result<T> success(T data) {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), data);
    }

    /**
     * 成功响应（自定义消息）
     */
    public static <T> Result<T> success(String message, T data) {
        return new Result<>(ResultCode.SUCCESS.getCode(), message, data);
    }

    /**
     * 失败响应
     */
    public static <T> Result<T> error(int code, String message) {
        return new Result<>(code, message, null);
    }

    /**
     * 失败响应（使用ResultCode）
     */
    public static <T> Result<T> error(ResultCode resultCode) {
        return new Result<>(resultCode.getCode(), resultCode.getMessage(), null);
    }

    /**
     * 失败响应（使用ResultCode，自定义消息）
     */
    public static <T> Result<T> error(ResultCode resultCode, String message) {
        return new Result<>(resultCode.getCode(), message, null);
    }

    /**
     * 参数错误
     */
    public static <T> Result<T> badRequest(String message) {
        return new Result<>(ResultCode.BAD_REQUEST.getCode(), message, null);
    }

    /**
     * 未认证
     */
    public static <T> Result<T> unauthorized(String message) {
        return new Result<>(ResultCode.UNAUTHORIZED.getCode(), message, null);
    }

    /**
     * 无权限
     */
    public static <T> Result<T> forbidden(String message) {
        return new Result<>(ResultCode.FORBIDDEN.getCode(), message, null);
    }

    /**
     * 资源不存在
     */
    public static <T> Result<T> notFound(String message) {
        return new Result<>(ResultCode.NOT_FOUND.getCode(), message, null);
    }

    /**
     * 分页响应
     */
    public static <T> Result<PageResult<T>> page(java.util.List<T> list, long total, int page, int size) {
        return success(new PageResult<>(list, total, page, size));
    }

    /**
     * 判断是否成功
     */
    public boolean isSuccess() {
        return this.code == ResultCode.SUCCESS.getCode();
    }
}
