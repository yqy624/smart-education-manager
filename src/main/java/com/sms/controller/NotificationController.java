package com.sms.controller;

import com.sms.dto.ApiResponse;
import com.sms.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'TEACHER', 'STUDENT')")
@Tag(name = "Notifications", description = "通知中心接口")
@SecurityRequirement(name = "bearerAuth")
public class NotificationController {

    private final NotificationService notificationService;

    @Operation(summary = "查看我的通知", description = "返回当前登录用户的全部通知列表。")
    @GetMapping
    public ApiResponse<?> myNotifications(@Parameter(hidden = true) Authentication auth) {
        return ApiResponse.ok(notificationService.getMyNotifications(auth.getName()));
    }

    @Operation(summary = "查看未读通知数", description = "返回当前登录用户的未读通知数量。")
    @GetMapping("/unread-count")
    public ApiResponse<?> unreadCount(@Parameter(hidden = true) Authentication auth) {
        return ApiResponse.ok(Map.of("count", notificationService.getUnreadCount(auth.getName())));
    }

    @Operation(summary = "标记单条通知为已读", description = "将指定通知标记为已读。")
    @PutMapping("/{id}/read")
    public ApiResponse<?> markRead(@Parameter(description = "通知 ID") @PathVariable Long id,
                                   @Parameter(hidden = true) Authentication auth) {
        notificationService.markAsRead(id, auth.getName());
        return ApiResponse.ok("已标记已读");
    }

    @Operation(summary = "全部标记为已读", description = "将当前登录用户的所有通知标记为已读。")
    @PutMapping("/read-all")
    public ApiResponse<?> markAllRead(@Parameter(hidden = true) Authentication auth) {
        notificationService.markAllAsRead(auth.getName());
        return ApiResponse.ok("全部已读");
    }
}
