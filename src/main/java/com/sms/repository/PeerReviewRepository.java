package com.sms.repository;

import com.sms.model.Assignment;
import com.sms.model.PeerReview;
import com.sms.model.PeerReviewStatus;
import com.sms.model.Submission;
import com.sms.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PeerReviewRepository extends JpaRepository<PeerReview, Long> {
    List<PeerReview> findByAssignment(Assignment assignment);
    List<PeerReview> findByAssignmentAndReviewer(Assignment assignment, User reviewer);
    List<PeerReview> findByReviewer(User reviewer);
    List<PeerReview> findByReviewerAndStatus(User reviewer, PeerReviewStatus status);
    List<PeerReview> findByTargetSubmission(Submission targetSubmission);
    Optional<PeerReview> findByAssignmentAndReviewerAndTargetSubmission(Assignment assignment, User reviewer, Submission targetSubmission);
    long countByAssignmentAndReviewerAndStatus(Assignment assignment, User reviewer, PeerReviewStatus status);
}
