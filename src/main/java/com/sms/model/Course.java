package com.sms.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "courses")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Course {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private String description;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "teacher_id", nullable = false)
    @ToString.Exclude
    private User teacher;

    @Column(length = 50)
    private String schedule;

    @Builder.Default
    private int credits = 3;

    @Builder.Default
    private int maxStudents = 50;

    @Builder.Default
    private int enrolledCount = 0;

    @Column(length = 100)
    private String category;

    @Builder.Default
    private boolean visible = true;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
