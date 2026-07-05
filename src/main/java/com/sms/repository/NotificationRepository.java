package com.sms.repository;

import com.sms.model.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 通知仓库。
 * 【新增文件 - 模块1：消息通知系统】
 */
@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /** 查询某用户的全部通知，按时间倒序 */
    List<Notification> findByRecipientOrderByCreatedAtDesc(String recipient);

    /** 查询某用户的未读通知 */
    List<Notification> findByRecipientAndReadFalseOrderByCreatedAtDesc(String recipient);

    /** 统计某用户未读数量 */
    long countByRecipientAndReadFalse(String recipient);

    List<Notification> findTop5ByRecipientAndCategoryOrderByCreatedAtDesc(String recipient, String category);

    Optional<Notification> findTopByRecipientAndCategoryOrderByCreatedAtDesc(String recipient, String category);
}
