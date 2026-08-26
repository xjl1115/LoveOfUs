package com.example.lovemap.ai.context;

/**
 * AI 工具调用线程上下文
 * <p>
 * AI 工具（@Tool 方法）由 LangChain4j 在 Agent 调用链中执行，脱离了 HTTP 请求上下文。
 * 在 Controller 入口处 setCurrentUser(userId, groupId)，工具内部使用 requireUserId()/requireGroupId()
 * 获取当前请求的用户信息；务必配套调用 clear()，避免 ThreadLocal 泄漏。
 */
public final class AiUserContext {

    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<Long> GROUP_ID = new ThreadLocal<>();

    private AiUserContext() {}

    public static void set(Long userId, Long groupId) {
        USER_ID.set(userId);
        GROUP_ID.set(groupId);
    }

    public static void clear() {
        USER_ID.remove();
        GROUP_ID.remove();
    }

    /**
     * 获取当前用户 ID（必填；未设置时抛异常，避免工具"裸奔"）
     */
    public static Long requireUserId() {
        Long id = USER_ID.get();
        if (id == null) {
            throw new IllegalStateException("AI 工具上下文缺失 userId，请检查调用链");
        }
        return id;
    }

    /**
     * 获取当前用户 groupId（必填）
     */
    public static Long requireGroupId() {
        Long gid = GROUP_ID.get();
        if (gid == null) {
            throw new IllegalStateException("AI 工具上下文缺失 groupId，请检查调用链");
        }
        return gid;
    }

    public static Long peekUserId() {
        return USER_ID.get();
    }

    public static Long peekGroupId() {
        return GROUP_ID.get();
    }
}