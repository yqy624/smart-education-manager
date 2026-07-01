package com.sms.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sms.model.Notification;
import com.sms.repository.NotificationRepository;
import com.sms.websocket.NotificationWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 通知服务。
 * 【新增文件 - 模块1：消息通知系统】
 * 统一入口：创建通知 = 持久化到数据库 + 尝试 WebSocket 实时推送。
 * 这样用户在线时即时收到，离线时下次登录可从库中拉取历史/未读。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationWebSocketHandler wsHandler;
    private final ObjectMapper objectMapper;

    /**
     * 发送一条通知给指定用户。
     * @param recipient 接收者用户名
     * @param type      ASSIGNMENT / GRADE / SYSTEM
     */
    @Transactional
    public Notification notify(String recipient, String type, String title, String content, String link) {
        Notification n = Notification.builder()
                .recipient(recipient).type(type)
                .title(title).content(content).link(link)
                .read(false)
                .build();
        n = notificationRepository.save(n);

        // 实时推送（用户不在线时静默跳过，消息已落库）
        try {
            wsHandler.sendToUser(recipient, objectMapper.writeValueAsString(n));
        } catch (Exception e) {
            log.warn("通知序列化失败: {}", e.getMessage());
        }
        return n;
    }

    /** 批量通知（如给某课程的所有学生发新作业通知） */
    @Transactional
    public void notifyAll(List<String> recipients, String type, String title, String content, String link) {
        for (String r : recipients) {
            notify(r, type, title, content, link);
        }
    }

    public List<Notification> getMyNotifications(String username) {
        return notificationRepository.findByRecipientOrderByCreatedAtDesc(username);
    }

    public long getUnreadCount(String username) {
        return notificationRepository.countByRecipientAndReadFalse(username);
    }

    @Transactional
    public void markAsRead(Long id, String username) {
        notificationRepository.findById(id)
            .filter(n -> n.getRecipient().equals(username)) // 只能标记自己的通知
            .ifPresent(n -> { n.setRead(true); notificationRepository.save(n); });
    }

    @Transactional
    public void markAllAsRead(String username) {
        List<Notification> unread = notificationRepository
            .findByRecipientAndReadFalseOrderByCreatedAtDesc(username);
        unread.forEach(n -> n.setRead(true));
        notificationRepository.saveAll(unread);
    }
}
