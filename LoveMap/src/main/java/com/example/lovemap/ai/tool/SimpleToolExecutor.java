package com.example.lovemap.ai.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 极简 ToolExecutor：替代 1.18 缺失的 DefaultToolExecutor。
 * <p>
 * 职责：根据 toolName 在 toolBeanMap 中找到 Bean，按方法签名 + JSON 参数调用对应方法。
 * 不处理 ToolMemoryId / 嵌套对象转换——目前所有工具方法的参数都是 String/Long/Integer/Boolean，
 * JSON 解析已足够。
 */
@Slf4j
public class SimpleToolExecutor {

    /** toolName -> 工具 Bean */
    private final Map<String, Object> toolBeanMap;
    private final ObjectMapper objectMapper;

    public SimpleToolExecutor(Map<String, Object> toolBeanMap, ObjectMapper objectMapper) {
        this.toolBeanMap = toolBeanMap;
        this.objectMapper = objectMapper;
    }

    /**
     * 执行 ToolExecutionRequest
     *
     * @return 工具方法的返回值（任意类型，调用方负责序列化回给 LLM）
     */
    public Object execute(ToolExecutionRequest request) {
        String name = request.name();
        Object bean = toolBeanMap.get(name);
        if (bean == null) {
            return "{\"error\":\"unknown tool: " + name + "\"}";
        }
        Method targetMethod = findMethod(bean.getClass(), name);
        if (targetMethod == null) {
            return "{\"error\":\"no method matching tool name: " + name + "\"}";
        }

        Object[] args = coerceArgs(targetMethod, request.arguments());
        try {
            log.info("[AI-TOOL] 执行 {}.{} args={}",
                    bean.getClass().getSimpleName(), targetMethod.getName(), request.arguments());
            Object result = targetMethod.invoke(bean, args);
            return result == null ? "{}" : result;
        } catch (ReflectiveOperationException e) {
            log.error("[AI-TOOL] 执行失败 tool={}", name, e);
            return "{\"error\":\"tool execution failed: " + e.getCause().getMessage() + "\"}";
        }
    }

    private Method findMethod(Class<?> beanClass, String name) {
        // 优先按 @Tool.name() 匹配（注解值即方法名一致最常见）
        for (Method m : beanClass.getDeclaredMethods()) {
            dev.langchain4j.agent.tool.Tool annotation = m.getAnnotation(dev.langchain4j.agent.tool.Tool.class);
            if (annotation != null) {
                String toolName = annotation.name().isEmpty() ? m.getName() : annotation.name();
                if (toolName.equals(name)) return m;
            }
        }
        // 退化按方法名匹配
        for (Method m : beanClass.getDeclaredMethods()) {
            if (m.getName().equals(name) && m.getAnnotation(dev.langchain4j.agent.tool.Tool.class) != null) {
                return m;
            }
        }
        return null;
    }

    /**
     * 把 JSON 参数串解析为 Object[]，按方法形参顺序填入。
     */
    private Object[] coerceArgs(Method method, String jsonArgs) {
        Class<?>[] paramTypes = method.getParameterTypes();
        if (paramTypes.length == 0) return new Object[0];
        Object[] result = new Object[paramTypes.length];
        Map<String, Object> map = parseJsonArgs(jsonArgs);
        java.lang.reflect.Parameter[] params = method.getParameters();
        for (int i = 0; i < params.length; i++) {
            String paramName = params[i].getName();
            Object value = map.get(paramName);
            result[i] = convert(value, paramTypes[i]);
        }
        return result;
    }

    private Map<String, Object> parseJsonArgs(String json) {
        if (json == null || json.isBlank() || "{}".equals(json.trim())) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            log.warn("[AI-TOOL] JSON 参数解析失败: {}", json, e);
            return new HashMap<>();
        }
    }

    private Object convert(Object value, Class<?> targetType) {
        if (value == null) return null;
        if (targetType.isInstance(value)) return value;
        try {
            if (targetType == Long.class || targetType == long.class) {
                return value instanceof Number n ? n.longValue() : Long.parseLong(value.toString());
            }
            if (targetType == Integer.class || targetType == int.class) {
                return value instanceof Number n ? n.intValue() : Integer.parseInt(value.toString());
            }
            if (targetType == Double.class || targetType == double.class) {
                return value instanceof Number n ? n.doubleValue() : Double.parseDouble(value.toString());
            }
            if (targetType == Boolean.class || targetType == boolean.class) {
                return Boolean.parseBoolean(value.toString());
            }
            if (targetType == List.class) {
                return objectMapper.convertValue(value, List.class);
            }
            if (targetType == String.class) return value.toString();
            return objectMapper.convertValue(value, targetType);
        } catch (Exception e) {
            log.warn("[AI-TOOL] 参数转换失败 value={} targetType={}", value, targetType, e);
            return null;
        }
    }

    public Map<String, Object> getToolBeanMap() {
        return toolBeanMap;
    }
}