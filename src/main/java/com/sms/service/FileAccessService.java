package com.sms.service;

import com.sms.model.Assignment;
import com.sms.model.RoleType;
import com.sms.model.StoredFile;
import com.sms.model.StoredFileCategory;
import com.sms.model.Submission;
import com.sms.model.User;
import com.sms.repository.AssignmentRepository;
import com.sms.repository.EnrollmentRepository;
import com.sms.repository.SubmissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FileAccessService {

    private final EnrollmentRepository enrollmentRepository;
    private final AssignmentRepository assignmentRepository;
    private final SubmissionRepository submissionRepository;

    public void assertCanAccess(StoredFile file, User user) {
        if (file == null) {
            throw new RuntimeException("文件不存在");
        }
        if (user.getRole() == RoleType.ADMIN) {
            return;
        }
        if (file.getCategory() == StoredFileCategory.SUBMISSION_ATTACHMENT) {
            assertSubmissionAccess(file, user);
            return;
        }
        if (file.getCategory() == StoredFileCategory.ASSIGNMENT_ATTACHMENT) {
            assertAssignmentAccess(file, user);
            return;
        }
        if (file.getUploaderUserId() != null && file.getUploaderUserId().equals(user.getId())) {
            return;
        }
        throw new RuntimeException("无权访问该文件");
    }

    public StoredFileCategory resolveCategory(String category) {
        if (category == null || category.isBlank()) {
            return StoredFileCategory.SUBMISSION_ATTACHMENT;
        }
        try {
            return StoredFileCategory.valueOf(category.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("无效的文件分类");
        }
    }

    private void assertSubmissionAccess(StoredFile file, User user) {
        Submission submission = file.getSubmissionId() == null ? null : submissionRepository.findById(file.getSubmissionId()).orElse(null);
        if (submission == null) {
            throw new RuntimeException("无权访问该文件");
        }
        if (user.getRole() == RoleType.STUDENT && submission.getStudent().getId().equals(user.getId())) {
            return;
        }
        if (user.getRole() == RoleType.TEACHER && submission.getAssignment() != null && submission.getAssignment().getTeacher() != null
            && submission.getAssignment().getTeacher().getId().equals(user.getId())) {
            return;
        }
        throw new RuntimeException("无权访问该文件");
    }

    private void assertAssignmentAccess(StoredFile file, User user) {
        Assignment assignment = file.getAssignmentId() == null ? null : assignmentRepository.findById(file.getAssignmentId()).orElse(null);
        if (assignment == null) {
            throw new RuntimeException("附件记录无效");
        }
        if (user.getRole() == RoleType.TEACHER && assignment.getTeacher() != null && assignment.getTeacher().getId().equals(user.getId())) {
            return;
        }
        if (user.getRole() == RoleType.STUDENT && enrollmentRepository.findByStudentAndCourse(user, assignment.getCourse()).isPresent()) {
            return;
        }
        throw new RuntimeException("无权访问该文件");
    }
}
