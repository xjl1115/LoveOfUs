package com.example.lovemap.ai.exception;

/**
 * AI 服务未开启 / 未配置 Key 时抛出
 * 由 GlobalExceptionHandler 转为 503
 */
public class AiDisabledException extends RuntimeException {

    public AiDisabledException(String message) {
        super(message);
    }
}