package com.example.lovemap.config;

import com.example.lovemap.common.Result;
import com.example.lovemap.common.ResultCode;
import com.example.lovemap.utils.JwtUtils;
import com.example.lovemap.utils.TokenUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

/**
 * JWT 认证过滤器
 * <p>
 * 拦截除认证接口外的所有请求，校验 JWT Token 的有效性：
 * 1. 放行 /auth/**、/swagger-ui/**、/v3/api-docs/** 等公开接口
 * 2. 从 Authorization 头提取 Token
 * 3. 验证 Token 是否有效（未过期、不在黑名单）
 * 4. 无效则返回 401 JSON 响应
 */
@Slf4j
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final TokenUtils tokenUtils;
    private final JwtUtils jwtUtils;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public JwtAuthFilter(TokenUtils tokenUtils, JwtUtils jwtUtils) {
        this.tokenUtils = tokenUtils;
        this.jwtUtils = jwtUtils;
    }

    /**
     * 公开路径匹配器
     */
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    /**
     * 无需认证的路径列表
     * 包含 /api 前缀的完整路径
     * 只放行真正公开的接口，需要登录的接口不走此排除列表
     */
    private static final List<String> EXCLUDED_PATHS = List.of(
            "/api/auth/captcha/**",
            "/api/auth/register",
            "/api/auth/login",
            "/api/auth/password/reset",
            "/api/auth/token/refresh",
            "/api/swagger-ui/**",
            "/api/v3/api-docs/**",
            "/api/error",
            // WebSocket 握手鉴权由 ChatHandshakeInterceptor 自行处理
            "/api/ws/**"
    );

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // 异步 Dispatch / 错误转发 / 包含转发 不需要重新校验 Token，
        // 这些 dispatcher 类型通常发生在 Spring MVC 异步完成（SSE / DeferredResult / Async 任务）
        // 或错误页渲染路径，原始请求线程已通过校验
        DispatcherType type = request.getDispatcherType();
        if (type == DispatcherType.ASYNC
                || type == DispatcherType.FORWARD
                || type == DispatcherType.INCLUDE
                || type == DispatcherType.ERROR) {
            return true;
        }

        String path = request.getRequestURI();
        // 使用完整路径进行匹配
        return EXCLUDED_PATHS.stream().anyMatch(pattern -> PATH_MATCHER.match(pattern, path));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {

        String authorization = request.getHeader("Authorization");
        String tokenParam = request.getParameter("token");

        // 1. 优先从 Authorization 头获取 Token，其次从 URL 参数获取（SSE 场景）
        String pureToken;
        if (authorization != null && authorization.startsWith(TokenUtils.BEARER_PREFIX)) {
            pureToken = tokenUtils.extractToken(authorization);
        } else if (tokenParam != null && !tokenParam.isEmpty()) {
            pureToken = tokenParam;
        } else {
            writeUnauthorizedResponse(response, "缺少认证Token，请先登录");
            return;
        }

        // 2. 验证 Token
        if (!tokenUtils.validateToken(pureToken)) {
            if (jwtUtils.getRemainingTime(pureToken) != null && jwtUtils.getRemainingTime(pureToken) <= 0) {
                writeUnauthorizedResponse(response, "Token已过期，请重新登录");
            } else {
                writeUnauthorizedResponse(response, "Token无效，请重新登录");
            }
            return;
        }

        // 3. 验证通过，将用户信息注入 SecurityContext
        Integer userId = tokenUtils.getUserId(pureToken);
        if (userId == null) {
            writeUnauthorizedResponse(response, "Token解析失败，请重新登录");
            return;
        }

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        userId,           // principal: 用户ID
                        null,             // credentials: 无需密码
                        Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
                );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        // 将 userId 放入 request attribute，方便后续通过 @RequestAttribute 获取
        request.setAttribute("userId", userId);
        request.setAttribute("token", pureToken);

        filterChain.doFilter(request, response);
    }

    /**
     * 写入 401 JSON 响应
     */
    private void writeUnauthorizedResponse(HttpServletResponse response, String message) throws IOException {
        // 响应已提交（例如 Controller 已经写出文件流），无法再写错误体，直接返回
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        Result<Void> result = Result.error(ResultCode.UNAUTHORIZED, message);
        String json = objectMapper.writeValueAsString(result);
        response.getWriter().write(json);
    }
}
