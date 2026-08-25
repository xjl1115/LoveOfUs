package com.example.lovemap.config;

import com.example.lovemap.common.Result;
import com.example.lovemap.common.ResultCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security 安全配置
 * 开放认证相关接口，其他接口需要认证
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // 禁用 CSRF（前后端分离）
            .csrf(csrf -> csrf.disable())
            // 无状态会话（使用 JWT Token）
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // 请求权限配置
            .authorizeHttpRequests(auth -> auth
                // 异步 Dispatch / 错误转发 / 包含转发 不再应用 Security 过滤链，
                // 避免 SSE (SseEmitter) 异步完成时 SecurityContext 丢失，
                // 触发 AuthorizationDeniedException + "response is already committed"
                .dispatcherTypeMatchers(
                        DispatcherType.ASYNC,
                        DispatcherType.FORWARD,
                        DispatcherType.INCLUDE,
                        DispatcherType.ERROR
                ).permitAll()
                // 开放认证相关接口（与 JwtAuthFilter.EXCLUDED_PATHS 保持一致）
                // 注意：requestMatchers 使用相对于 context-path 的路径，不包含 /api 前缀
                .requestMatchers(
                    "/auth/captcha/**",
                    "/auth/register",
                    "/auth/login",
                    "/auth/password/reset",
                    "/auth/token/refresh",
                    // 聊天 WebSocket（握手时通过拦截器自行鉴权）
                    "/ws/**",
                    // Tomcat 内部错误转发与错误页面，避免鉴权链二次触发导致
                    // "response is already committed" 死循环
                    "/error",
                    "/error/**"
                ).permitAll()
                // Swagger 文档
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                // 其他请求需要认证
                .anyRequest().authenticated()
            )
            // 在 UsernamePasswordAuthenticationFilter 之前添加 JWT 认证过滤器
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
            // 禁用默认的登录表单
            .formLogin(form -> form.disable())
            // 禁用 HTTP Basic
            .httpBasic(basic -> basic.disable())
            // 配置异常处理
            .exceptionHandling(exception -> exception
                    .authenticationEntryPoint(authenticationEntryPoint())
                    .accessDeniedHandler(accessDeniedHandler())
            );

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 自定义认证入口点 - 处理未认证请求（返回401）
     * <p>
     * 若响应已提交（例如已写出文件流），则只能放弃写入 JSON，
     * 否则 Tomcat 会抛 "response is already committed" 异常。
     */
    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, authException) -> {
            if (response.isCommitted()) {
                return;
            }
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            Result<Void> result = Result.error(ResultCode.UNAUTHORIZED, "请先登录");
            response.getWriter().write(new ObjectMapper().writeValueAsString(result));
        };
    }

    /**
     * 自定义访问拒绝处理器 - 处理无权限请求（返回403）
     * <p>
     * 同样避免在 response 已 committed 时写入，否则会触发 ServletException。
     */
    @Bean
    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, accessDeniedException) -> {
            if (response.isCommitted()) {
                return;
            }
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            Result<Void> result = Result.error(ResultCode.FORBIDDEN, "无权限访问该资源");
            response.getWriter().write(new ObjectMapper().writeValueAsString(result));
        };
    }
}
