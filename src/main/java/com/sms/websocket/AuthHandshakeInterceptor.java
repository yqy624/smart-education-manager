package com.sms.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * WebSocket 握手拦截器。
 * 【新增文件 - 模块1：消息通知系统】
 * 在握手阶段从 ?token= 解析出用户名并放入 session attributes，供 Handler 使用。
 * 鉴权失败则拒绝握手（返回 false）。
 */
@Component
@RequiredArgsConstructor
public class AuthHandshakeInterceptor implements HandshakeInterceptor {

    private final NotificationWebSocketHandler wsHandler;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandlerArg, Map<String, Object> attributes) {
        String username = wsHandler.resolveUsernameFromToken(request.getURI());
        if (username == null) {
            return false; // token 无效，拒绝握手
        }
        attributes.put("username", username);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandlerArg, Exception exception) {
        // no-op
    }
}
