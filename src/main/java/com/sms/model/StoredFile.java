package com.sms.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "stored_files", uniqueConstraints = @UniqueConstraint(columnNames = "storagePath"))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StoredFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String storagePath;

    @Column(nullable = false, length = 100)
    private String bucket;

    @Column(nullable = false, length = 255)
    private String objectKey;

    @Column(nullable = false, length = 255)
    private String originalName;

    @Column(length = 120)
    private String contentType;

    private Long size;

    @Column(length = 20)
    private String extension;

    private Long uploaderUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private StoredFileCategory category;

    private Long courseId;

    private Long assignmentId;

    private Long submissionId;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
