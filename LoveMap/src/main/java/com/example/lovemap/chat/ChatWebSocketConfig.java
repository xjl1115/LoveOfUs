package com.example.lovemap.chat;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * 注册聊天 WebSocket 端点
 * <p>
 * 端点：/ws/chat（context-path=/api，所以客户端连接 /api/ws/chat）
 * - 鉴权：握手拦截器通过 ?token= 或 Authorization 校验 JWT
 * - 支持多端登录（PC + 移动）
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class ChatWebSocketConfig implements WebSocketConfigurer {

    private final ChatWebSocketHandler chatWebSocketHandler;
    private final ChatHandshakeInterceptor chatHandshakeInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(chatWebSocketHandler, "/ws/chat")
                .addInterceptors(chatHandshakeInterceptor)
                .setAllowedOriginPatterns("*");
    }
}
