package com.sms.websocket;

import com.sms.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 站内通知 WebSocket 处理器。
 * 【新增文件 - 模块1：消息通知系统】
 *
 * 设计要点：
 * 1. 握手 URL 形如 ws://host/ws/notifications?token=JWT，复用项目已有的 JwtTokenProvider 做鉴权；
 *    （前端 WebSocket API 无法自定义请求头，因此用 query 参数传 token，与 JwtAuthFilter 的兼容方式一致）
 * 2. 用 username -> 多个 session 的映射保存在线连接（同一用户可能多端登录）；
 * 3. 对外暴露 sendToUser(username, json) 供 NotificationService 调用，实现定向推送。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationWebSocketHandler extends TextWebSocketHandler {

    private final JwtTokenProvider jwtTokenProvider;

    /** 在线会话：username -> 该用户的所有 WebSocket 连接 */
    private final Map<String, Set<WebSocketSession>> sessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String username = (String) session.getAttributes().get("username");
        if (username == null) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("未认证"));
            return;
        }
        sessions.computeIfAbsent(username, k -> ConcurrentHashMap.newKeySet()).add(session);
        log.debug("WebSocket 连接建立: {} (当前在线 {} 人)", username, sessions.size());
        // 连接成功后回一条握手确认消息
        session.sendMessage(new TextMessage("{\"type\":\"CONNECTED\",\"message\":\"实时通知已连接\"}"));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String username = (String) session.getAttributes().get("username");
        if (username != null) {
            Set<WebSocketSession> set = sessions.get(username);
            if (set != null) {
                set.remove(session);
                if (set.isEmpty()) sessions.remove(username);
            }
        }
    }

    /**
     * 向指定用户的所有在线连接推送一条消息（JSON 字符串）。
     * 若用户不在线，消息已由 NotificationService 持久化到库，下次登录可拉取。
     */
    public void sendToUser(String username, String jsonPayload) {
        Set<WebSocketSession> set = sessions.get(username);
        if (set == null || set.isEmpty()) return;
        TextMessage msg = new TextMessage(jsonPayload);
        for (WebSocketSession s : set) {
            try {
                if (s.isOpen()) s.sendMessage(msg);
            } catch (Exception e) {
                log.warn("推送通知失败: {}", e.getMessage());
            }
        }
    }

    /** 供拦截器在握手阶段解析 token 用户名 */
    public String resolveUsernameFromToken(URI uri) {
        if (uri == null || uri.getQuery() == null) return null;
        for (String pair : uri.getQuery().split("&")) {
            int i = pair.indexOf('=');
            if (i > 0 && "token".equals(pair.substring(0, i))) {
                String token = pair.substring(i + 1);
                if (jwtTokenProvider.validateToken(token)) {
                    return jwtTokenProvider.getUsername(token);
                }
            }
        }
        return null;
    }
}
