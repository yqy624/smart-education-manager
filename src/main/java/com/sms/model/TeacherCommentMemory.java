package com.sms.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "teacher_comment_memories", indexes = {
    @Index(name = "idx_teacher_comment_teacher_usage", columnList = "teacher_id, usageCount, lastUsedAt"),
    @Index(name = "idx_teacher_comment_teacher_category", columnList = "teacher_id, category"),
    @Index(name = "idx_teacher_comment_teacher_normalized", columnList = "teacher_id, normalizedText")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TeacherCommentMemory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "teacher_id", nullable = false)
    @ToString.Exclude
    private User teacher;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String commentText;

    @Column(nullable = false, length = 500)
    private String normalizedText;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private CommentCategory category;

    @Builder.Default
    private long usageCount = 0L;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime lastUsedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
