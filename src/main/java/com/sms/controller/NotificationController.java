package com.sms.controller;

import com.sms.dto.ApiResponse;
import com.sms.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 通知 REST 接口。
 * 【新增文件 - 模块1：消息通知系统】
 * 所有登录用户均可访问自己的通知。前端首次进入时拉取历史 + 未读数，
 * 之后通过 WebSocket 接收实时新通知。
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'TEACHER', 'STUDENT')")
public class NotificationController {

    private final NotificationService notificationService;

    /** 我的全部通知 */
    @GetMapping
    public ApiResponse<?> myNotifications(Authentication auth) {
        return ApiResponse.ok(notificationService.getMyNotifications(auth.getName()));
    }

    /** 未读数量（前端轮询/初始化用） */
    @GetMapping("/unread-count")
    public ApiResponse<?> unreadCount(Authentication auth) {
        return ApiResponse.ok(Map.of("count", notificationService.getUnreadCount(auth.getName())));
    }

    /** 标记单条已读 */
    @PutMapping("/{id}/read")
    public ApiResponse<?> markRead(@PathVariable Long id, Authentication auth) {
        notificationService.markAsRead(id, auth.getName());
        return ApiResponse.ok("已标记已读");
    }

    /** 全部标记已读 */
    @PutMapping("/read-all")
    public ApiResponse<?> markAllRead(Authentication auth) {
        notificationService.markAllAsRead(auth.getName());
        return ApiResponse.ok("全部已读");
    }
}
