package com.example.lovemap.ai.exception;

/**
 * AI 服务异常 → 前端可读文案
 * <p>
 * DashScope 额度不足时抛出的异常 message 是一段英文 JSON（如
 * {@code {"code":"AllocationQuota.FreeTierOnly", ...}}），直接透出给用户可读性差，
 * 这里统一转换为中文提示。判断基于 message/code 关键字，因此对异常是否被包装不做假设。
 */
public final class AiErrorMessages {

    /** 额度不足（免费额度用尽 / 欠费 / 配额限流）统一提示 */
    public static final String QUOTA_EXHAUSTED = "AI 服务额度已用尽，请稍后再试或联系管理员";

    /** 无法提取有效信息时的兜底提示 */
    private static final String DEFAULT_MESSAGE = "AI 服务暂时不可用，请稍后再试";

    /** message 中出现以下关键字（忽略大小写）即判定为额度不足 */
    private static final String[] QUOTA_MARKERS = {
            "AllocationQuota",
            "FreeTierOnly",
            "Free quota exhausted",
            "free tier",
            "quota",
            "Arrearage",
            "欠费"
    };

    private static final int MAX_MESSAGE_LENGTH = 200;

    /** cause 链遍历保护上限，避免异常自引用导致死循环 */
    private static final int MAX_CAUSE_DEPTH = 10;

    private AiErrorMessages() {
    }

    /**
     * 判断异常（含 cause 链）是否为额度不足类错误
     */
    public static boolean isQuotaExhausted(Throwable error) {
        Throwable current = error;
        for (int depth = 0; current != null && depth < MAX_CAUSE_DEPTH; depth++, current = current.getCause()) {
            String message = current.getMessage();
            if (message == null) {
                continue;
            }
            String lower = message.toLowerCase();
            for (String marker : QUOTA_MARKERS) {
                if (lower.contains(marker.toLowerCase())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 转换为可直接展示给前端的文案
     */
    public static String toUserMessage(Throwable error) {
        if (isQuotaExhausted(error)) {
            return QUOTA_EXHAUSTED;
        }
        String message = firstMessage(error);
        if (message == null) {
            return DEFAULT_MESSAGE;
        }
        return message.length() > MAX_MESSAGE_LENGTH
                ? message.substring(0, MAX_MESSAGE_LENGTH) + "…"
                : message;
    }

    /**
     * 取异常链中第一个非空 message
     */
    public static String firstMessage(Throwable error) {
        Throwable current = error;
        for (int depth = 0; current != null && depth < MAX_CAUSE_DEPTH; depth++, current = current.getCause()) {
            String message = current.getMessage();
            if (message != null && !message.isBlank()) {
                return message;
            }
        }
        return null;
    }
}
