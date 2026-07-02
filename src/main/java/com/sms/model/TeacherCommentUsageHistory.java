package com.sms.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "teacher_comment_usage_history", indexes = {
    @Index(name = "idx_teacher_comment_usage_teacher_used", columnList = "teacher_id, usedAt"),
    @Index(name = "idx_teacher_comment_usage_memory_used", columnList = "memory_id, usedAt"),
    @Index(name = "idx_teacher_comment_usage_submission", columnList = "submission_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TeacherCommentUsageHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "teacher_id", nullable = false)
    @ToString.Exclude
    private User teacher;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "memory_id")
    @ToString.Exclude
    private TeacherCommentMemory memory;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "submission_id", nullable = false)
    @ToString.Exclude
    private Submission submission;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String commentSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private CommentCategory categorySnapshot;

    private Double scoreSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private CommentUsageSource sourceType;

    @Builder.Default
    private LocalDateTime usedAt = LocalDateTime.now();

    @PrePersist
    protected void onCreate() {
        if (usedAt == null) {
            usedAt = LocalDateTime.now();
        }
    }
}
