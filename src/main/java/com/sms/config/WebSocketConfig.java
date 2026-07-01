package com.sms.config;

import com.sms.websocket.AuthHandshakeInterceptor;
import com.sms.websocket.NotificationWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket 配置。
 * 【新增文件 - 模块1：消息通知系统】
 * 注册通知端点 /ws/notifications，并挂上鉴权握手拦截器。
 * setAllowedOrigins("*") 便于本地开发；生产环境应收紧为具体域名。
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final NotificationWebSocketHandler notificationWebSocketHandler;
    private final AuthHandshakeInterceptor authHandshakeInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(notificationWebSocketHandler, "/ws/notifications")
                .addInterceptors(authHandshakeInterceptor)
                .setAllowedOrigins("*");
    }
}
