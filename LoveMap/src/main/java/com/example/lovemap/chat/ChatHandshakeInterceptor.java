package com.example.lovemap.chat;

import com.example.lovemap.utils.JwtUtils;
import com.example.lovemap.utils.TokenUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * 聊天 WebSocket 握手拦截器
 * <p>
 * 在握手阶段校验 Token：
 * 1. 优先 Authorization 头
 * 2. 次选 ?token=xxx 查询参数（浏览器原生 WebSocket 不支持自定义头）
 * <p>
 * 校验通过后将 userId 放入 attributes，供后续 Session 使用。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatHandshakeInterceptor implements HandshakeInterceptor {

    public static final String ATTR_USER_ID = "chat.userId";
    public static final String ATTR_TOKEN = "chat.token";

    private final TokenUtils tokenUtils;
    private final JwtUtils jwtUtils;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {
        String token = extractToken(request);
        if (token == null) {
            log.warn("聊天 WebSocket 握手失败：未通过 Token 鉴权, remote: {}", request.getRemoteAddress());
            response.setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);
            return false;
        }
        if (!tokenUtils.validateToken(token)) {
            log.warn("聊天 WebSocket 握手失败：Token 不合法, remote: {}", request.getRemoteAddress());
            response.setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);
            return false;
        }
        // 校验 Token 是否在黑名单（已被登出/注销的 Token 不可用于建立新 WS）
        if (tokenUtils.isTokenBlacklisted(token)) {
            log.warn("聊天 WebSocket 握手拒绝：Token 已加入黑名单, remote: {}", request.getRemoteAddress());
            response.setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);
            return false;
        }
        Integer userId = tokenUtils.getUserId(token);
        attributes.put(ATTR_USER_ID, userId);
        attributes.put(ATTR_TOKEN, token);
        log.info("聊天 WebSocket 握手成功, userId: {}, remote: {}", userId, request.getRemoteAddress());
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request,
                               ServerHttpResponse response,
                               WebSocketHandler wsHandler,
                               Exception exception) {
        // no-op
    }

    private String extractToken(ServerHttpRequest request) {
        String token = null;

        // 1. Authorization 头
        var authHeader = request.getHeaders().getFirst("Authorization");
        if (authHeader != null && authHeader.startsWith(TokenUtils.BEARER_PREFIX)) {
            token = tokenUtils.extractToken(authHeader);
        }

        // 2. URL 查询参数 ?token=xxx
        if (token == null && request instanceof ServletServerHttpRequest servletReq) {
            token = servletReq.getServletRequest().getParameter("token");
        }

        // 3. Sec-WebSocket-Protocol（subprotocol，浏览器可设置，但通常不用）
        if (token == null) {
            var protocols = request.getHeaders().get("Sec-WebSocket-Protocol");
            if (protocols != null) {
                for (String p : protocols) {
                    if (p != null && !p.isEmpty()) {
                        token = p;
                        break;
                    }
                }
            }
        }

        if (token == null || token.isEmpty()) {
            return null;
        }
        return token;
    }
}
