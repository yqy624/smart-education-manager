package com.sms.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "peer_reviews", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"assignment_id", "reviewer_id", "target_submission_id"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PeerReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "assignment_id", nullable = false)
    @ToString.Exclude
    private Assignment assignment;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "reviewer_id", nullable = false)
    @ToString.Exclude
    private User reviewer;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "target_submission_id", nullable = false)
    @ToString.Exclude
    private Submission targetSubmission;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    private PeerReviewStatus status = PeerReviewStatus.ASSIGNED;

    private Integer rating;

    @Column(columnDefinition = "TEXT")
    private String comment;

    @Builder.Default
    private LocalDateTime assignedAt = LocalDateTime.now();

    private LocalDateTime submittedAt;

    private LocalDateTime bonusGrantedAt;
}
