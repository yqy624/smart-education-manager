package com.sms.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "audit_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 50)
    private String username;

    @Column(length = 20)
    private String role;

    @Column(nullable = false, length = 255)
    private String action;

    @Column(length = 500)
    private String details;

    @Column(length = 50)
    private String ipAddress;

    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
}
