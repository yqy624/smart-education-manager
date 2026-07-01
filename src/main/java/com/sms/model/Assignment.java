package com.sms.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "assignments")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Assignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "course_id", nullable = false)
    @ToString.Exclude
    private Course course;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "teacher_id", nullable = false)
    @ToString.Exclude
    private User teacher;

    private LocalDateTime dueDate;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(length = 500)
    private String attachmentPaths; // comma-separated file paths

    @Builder.Default
    private Integer totalPoints = 100;
}
