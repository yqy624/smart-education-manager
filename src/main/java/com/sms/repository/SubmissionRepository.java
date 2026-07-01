package com.sms.repository;

import com.sms.model.Assignment;
import com.sms.model.AssignmentStatus;
import com.sms.model.Submission;
import com.sms.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SubmissionRepository extends JpaRepository<Submission, Long> {
    List<Submission> findByAssignment(Assignment assignment);
    List<Submission> findByStudent(User student);
    Optional<Submission> findByAssignmentAndStudent(Assignment assignment, User student);
    long countByAssignmentAndStatus(Assignment assignment, AssignmentStatus status);
}
