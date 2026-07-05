package com.sms.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sms.model.Notification;
import com.sms.repository.NotificationRepository;
import com.sms.websocket.NotificationWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
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

    public static final String CATEGORY_PUBLISHED_ACTIVITY = "PUBLISHED_ACTIVITY";
    private static final String UNREAD_COUNT_CACHE = "notificationUnreadCount";

    private final NotificationRepository notificationRepository;
    private final NotificationWebSocketHandler wsHandler;
    private final ObjectMapper objectMapper;
    private final CacheManager cacheManager;

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
        evictUnreadCount(recipient);

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
            evictUnreadCount(r);
        }
    }

    public List<Notification> getMyNotifications(String username) {
        return notificationRepository.findByRecipientOrderByCreatedAtDesc(username);
    }

    public List<Notification> getPublishedActivities(String username, int limit) {
        if (limit <= 1) {
            return notificationRepository.findTopByRecipientAndCategoryOrderByCreatedAtDesc(username, CATEGORY_PUBLISHED_ACTIVITY)
                .map(List::of)
                .orElse(List.of());
        }
        return notificationRepository.findTop5ByRecipientAndCategoryOrderByCreatedAtDesc(username, CATEGORY_PUBLISHED_ACTIVITY);
    }

    @Transactional
    public void tagLatestActivities(List<String> recipients, String title, String content, String link) {
        for (String recipient : recipients) {
            List<Notification> latest = notificationRepository.findByRecipientOrderByCreatedAtDesc(recipient);
            latest.stream()
                .filter(n -> recipient.equals(n.getRecipient()))
                .filter(n -> title.equals(n.getTitle()))
                .filter(n -> content.equals(n.getContent()))
                .filter(n -> java.util.Objects.equals(link, n.getLink()))
                .findFirst()
                .ifPresent(n -> {
                    n.setCategory(CATEGORY_PUBLISHED_ACTIVITY);
                    notificationRepository.save(n);
                });
        }
    }

    @Cacheable(cacheNames = UNREAD_COUNT_CACHE, key = "#username")
    public long getUnreadCount(String username) {
        return notificationRepository.countByRecipientAndReadFalse(username);
    }

    @Transactional
    public void markAsRead(Long id, String username) {
        notificationRepository.findById(id)
            .filter(n -> n.getRecipient().equals(username)) // 只能标记自己的通知
            .ifPresent(n -> { n.setRead(true); notificationRepository.save(n); });
        evictUnreadCount(username);
    }

    @Transactional
    public void markAllAsRead(String username) {
        List<Notification> unread = notificationRepository
            .findByRecipientAndReadFalseOrderByCreatedAtDesc(username);
        unread.forEach(n -> n.setRead(true));
        notificationRepository.saveAll(unread);
        evictUnreadCount(username);
    }

    private void evictUnreadCount(String username) {
        Cache cache = cacheManager.getCache(UNREAD_COUNT_CACHE);
        if (cache != null) {
            cache.evict(username);
        }
    }
}
