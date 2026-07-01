package com.sms.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * 站内通知实体。
 * 【新增文件 - 模块1：消息通知系统】
 * 用于持久化每条通知，既支持 WebSocket 实时推送，也支持用户离线后登录查看历史通知。
 */
@Entity
@Table(name = "notifications")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 接收者用户名（与 User.username 对应，用它来定向推送/查询） */
    @Column(nullable = false, length = 50)
    private String recipient;

    /** 通知类型：ASSIGNMENT（新作业）、GRADE（评分）、SYSTEM（系统） */
    @Column(length = 20)
    private String type;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 500)
    private String content;

    /** 可选的跳转链接 / 关联业务ID，前端可据此跳转 */
    @Column(length = 100)
    private String link;

    @Column(length = 50)
    private String category;

    @Builder.Default
    @Column(name = "is_read")   // read 是 MySQL 保留字，映射为 is_read 列避免建表/查询报错
    private boolean read = false;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
