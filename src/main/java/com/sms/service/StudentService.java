package com.sms.service;

import com.sms.model.*;
import com.sms.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class StudentService {

    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final AssignmentRepository assignmentRepository;
    private final SubmissionRepository submissionRepository;
    private final PeerReviewRepository peerReviewRepository;
    private final NotificationService notificationService;

    public List<Course> getAllCourses() {
        return courseRepository.findByVisibleTrue();
    }

    public List<Course> getMyCourses(User student) {
        return enrollmentRepository.findByStudent(student).stream().map(Enrollment::getCourse).toList();
    }

    @Transactional
    public void enroll(Long courseId, User student) {
        Course course = courseRepository.findById(courseId).orElseThrow(() -> new RuntimeException("课程不存在"));
        if (!course.isVisible()) {
            throw new RuntimeException("课程已下架");
        }
        if (enrollmentRepository.existsByStudentAndCourse(student, course)) {
            throw new RuntimeException("已经选过该课程");
        }
        if (course.getEnrolledCount() >= course.getMaxStudents()) {
            throw new RuntimeException("课程已满");
        }
        List<Enrollment> existing = enrollmentRepository.findByStudent(student);
        boolean conflict = existing.stream().anyMatch(e -> sameDaySchedule(e.getCourse().getSchedule(), course.getSchedule()));
        if (conflict) {
            throw new RuntimeException("选课时间冲突");
        }
        enrollmentRepository.save(Enrollment.builder().student(student).course(course).build());
        course.setEnrolledCount(course.getEnrolledCount() + 1);
        courseRepository.save(course);
    }

    @Transactional
    public void drop(Long courseId, User student) {
        Course course = courseRepository.findById(courseId).orElseThrow(() -> new RuntimeException("课程不存在"));
        enrollmentRepository.findByStudentAndCourse(student, course).ifPresent(e -> {
            enrollmentRepository.delete(e);
            course.setEnrolledCount(course.getEnrolledCount() - 1);
            courseRepository.save(course);
        });
    }

    public List<Assignment> getMyAssignments(Long courseId, User student) {
        Course course = courseRepository.findById(courseId).orElseThrow(() -> new RuntimeException("课程不存在"));
        return assignmentRepository.findByCourse(course);
    }

    @Transactional
    public Submission submitAssignment(Long assignmentId, User student, String content) {
        return submitAssignment(assignmentId, student, content, null);
    }

    @Transactional
    public Submission submitAssignment(Long assignmentId, User student, String content, String filePaths) {
        Assignment assignment = assignmentRepository.findById(assignmentId).orElseThrow(() -> new RuntimeException("作业不存在"));
        Submission existing = submissionRepository.findByAssignmentAndStudent(assignment, student).orElse(null);
        if (existing == null) {
            existing = Submission.builder().assignment(assignment).student(student).build();
        }
        existing.setContent(content);
        existing.setFilePaths(filePaths);
        existing.setStatus(AssignmentStatus.SUBMITTED);
        existing.setSubmittedAt(LocalDateTime.now());
        return submissionRepository.save(existing);
    }

    public List<Map<String, Object>> getMyGrades(User student) {
        List<Map<String, Object>> grades = new ArrayList<>();
        for (Submission submission : submissionRepository.findByStudent(student)) {
            Assignment assignment = submission.getAssignment();
            Course course = assignment.getCourse();
            Enrollment enrollment = enrollmentRepository.findByStudentAndCourse(student, course).orElse(null);
            double bonus = enrollment != null ? enrollment.getPeerReviewBonus() : 0;
            Double teacherScore = submission.getScore();
            Double totalScore = teacherScore != null ? teacherScore + bonus : null;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("courseId", course.getId());
            item.put("courseName", course.getName());
            item.put("assignmentId", assignment.getId());
            item.put("assignmentTitle", assignment.getTitle());
            item.put("status", submission.getStatus());
            item.put("teacherScore", teacherScore);
            item.put("peerReviewBonus", bonus);
            item.put("score", totalScore);
            item.put("teacherComment", submission.getTeacherComment());
            item.put("content", submission.getContent());
            item.put("filePaths", submission.getFilePaths());
            grades.add(item);
        }
        return grades;
    }

    public List<Map<String, Object>> getMyPeerReviews(User student) {
        List<Map<String, Object>> groups = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (Course course : getMyCourses(student)) {
            for (Assignment assignment : assignmentRepository.findByCourse(course)) {
                Map<String, Object> group = buildPeerReviewGroup(course, assignment, student, now);
                if (group != null) {
                    groups.add(group);
                }
            }
        }
        groups.sort(Comparator.comparing(g -> String.valueOf(g.get("courseName")) + "_" + String.valueOf(g.get("assignmentTitle"))));
        return groups;
    }

    @Transactional
    public Map<String, Object> submitPeerReview(User reviewer, Long assignmentId, Long targetSubmissionId, Integer rating, String comment) {
        Assignment assignment = assignmentRepository.findById(assignmentId).orElseThrow(() -> new RuntimeException("作业不存在"));
        ensurePeerReviewOpen(assignment);

        Submission reviewerSubmission = submissionRepository.findByAssignmentAndStudent(assignment, reviewer)
            .orElseThrow(() -> new RuntimeException("请先提交自己的作业再参与互评"));
        if (reviewerSubmission.getStatus() == AssignmentStatus.PENDING) {
            throw new RuntimeException("请先提交自己的作业再参与互评");
        }

        Submission targetSubmission = submissionRepository.findById(targetSubmissionId)
            .orElseThrow(() -> new RuntimeException("互评目标不存在"));
        if (!targetSubmission.getAssignment().getId().equals(assignment.getId())) {
            throw new RuntimeException("互评目标不属于该作业");
        }
        if (targetSubmission.getStudent().getId().equals(reviewer.getId())) {
            throw new RuntimeException("不能评价自己的作业");
        }

        List<Submission> assignedTargets = getAssignedTargets(assignment, reviewer);
        boolean assigned = assignedTargets.stream().anyMatch(s -> s.getId().equals(targetSubmission.getId()));
        if (!assigned) {
            throw new RuntimeException("当前未分配该互评任务");
        }
        if (peerReviewRepository.findByAssignmentAndReviewerAndTargetSubmission(assignment, reviewer, targetSubmission).isPresent()) {
            throw new RuntimeException("该互评任务已提交");
        }

        String normalizedComment = comment == null ? "" : comment.trim();
        boolean qualifies = isReasonableReview(rating, normalizedComment);
        Enrollment enrollment = enrollmentRepository.findByStudentAndCourse(reviewer, assignment.getCourse())
            .orElseThrow(() -> new RuntimeException("请先选课"));

        double awarded = 0;
        PeerReview review = PeerReview.builder()
            .assignment(assignment)
            .reviewer(reviewer)
            .targetSubmission(targetSubmission)
            .rating(rating)
            .comment(normalizedComment)
            .submittedAt(LocalDateTime.now())
            .status(PeerReviewStatus.SUBMITTED)
            .build();

        if (qualifies) {
            double currentBonus = enrollment.getPeerReviewBonus();
            double nextBonus = Math.min(currentBonus + assignment.getPeerReviewBonusPerReview(), assignment.getPeerReviewBonusCap());
            awarded = Math.max(0, nextBonus - currentBonus);
            if (awarded > 0) {
                enrollment.setPeerReviewBonus(nextBonus);
                recalculateEnrollmentScore(enrollment);
                enrollmentRepository.save(enrollment);
                review.setStatus(PeerReviewStatus.BONUS_GRANTED);
                review.setBonusGrantedAt(LocalDateTime.now());
            }
        }

        review = peerReviewRepository.save(review);
        if (awarded > 0) {
            notificationService.notify(
                reviewer.getUsername(),
                "SYSTEM",
                "互评加分成功",
                "你完成了一次合格的匿名互评，已获得 " + awarded + " 分平时分。",
                "peer-review:" + review.getId()
            );
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("reviewId", review.getId());
        result.put("status", review.getStatus());
        result.put("bonusGranted", awarded > 0);
        result.put("bonusAwarded", awarded);
        result.put("totalBonus", enrollment.getPeerReviewBonus());
        result.put("targetSubmissionId", targetSubmissionId);
        return result;
    }

    public Double getCourseAverageScore(Long courseId) {
        Course course = courseRepository.findById(courseId).orElseThrow(() -> new RuntimeException("课程不存在"));
        return enrollmentRepository.findByCourse(course).stream()
            .filter(e -> e.getScore() >= 0)
            .mapToDouble(Enrollment::getScore)
            .average()
            .orElse(-1);
    }

    private Map<String, Object> buildPeerReviewGroup(Course course, Assignment assignment, User student, LocalDateTime now) {
        if (!isPeerReviewActive(assignment, now)) {
            List<PeerReview> existing = peerReviewRepository.findByAssignmentAndReviewer(assignment, student);
            if (existing.isEmpty()) {
                return null;
            }
        }
        Submission ownSubmission = submissionRepository.findByAssignmentAndStudent(assignment, student).orElse(null);
        if (ownSubmission == null || ownSubmission.getStatus() == AssignmentStatus.PENDING) {
            return null;
        }

        List<PeerReview> myReviews = peerReviewRepository.findByAssignmentAndReviewer(assignment, student);
        int requiredCount = Math.max(1, assignment.getPeerReviewRequiredCount() == null ? 1 : assignment.getPeerReviewRequiredCount());
        int remainingCount = Math.max(0, requiredCount - myReviews.size());
        Enrollment enrollment = enrollmentRepository.findByStudentAndCourse(student, course).orElse(null);

        Map<Long, PeerReview> reviewByTarget = new LinkedHashMap<>();
        for (PeerReview review : myReviews) {
            reviewByTarget.put(review.getTargetSubmission().getId(), review);
        }

        List<Submission> candidateTargets = getAssignedTargets(assignment, student);
        List<Map<String, Object>> candidateItems = candidateTargets.stream()
            .map(target -> buildCandidateTarget(target))
            .toList();

        List<Map<String, Object>> reviews = new ArrayList<>();
        for (PeerReview review : myReviews) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("targetSubmissionId", review.getTargetSubmission().getId());
            item.put("reviewId", review.getId());
            item.put("status", review.getStatus());
            item.put("rating", review.getRating());
            item.put("comment", review.getComment());
            item.put("submittedAt", review.getSubmittedAt());
            item.put("bonusGrantedAt", review.getBonusGrantedAt());
            item.put("preview", buildPreviewText(review.getTargetSubmission()));
            item.put("selectedTarget", buildCandidateTarget(review.getTargetSubmission()));
            item.put("candidateTargets", candidateItems);
            reviews.add(item);
        }

        if (remainingCount > 0 && isPeerReviewActive(assignment, now)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("targetSubmissionId", candidateTargets.isEmpty() ? null : candidateTargets.get(0).getId());
            item.put("reviewId", null);
            item.put("status", PeerReviewStatus.ASSIGNED);
            item.put("rating", null);
            item.put("comment", "");
            item.put("submittedAt", null);
            item.put("bonusGrantedAt", null);
            item.put("preview", candidateTargets.isEmpty() ? "" : buildPreviewText(candidateTargets.get(0)));
            item.put("selectedTarget", candidateTargets.isEmpty() ? null : buildCandidateTarget(candidateTargets.get(0)));
            item.put("candidateTargets", candidateItems);
            reviews.add(item);
        }

        Map<String, Object> group = new LinkedHashMap<>();
        group.put("assignmentId", assignment.getId());
        group.put("assignmentTitle", assignment.getTitle());
        group.put("courseId", course.getId());
        group.put("courseName", course.getName());
        group.put("prompt", assignment.getPeerReviewPrompt());
        group.put("requiredCount", requiredCount);
        group.put("completedCount", myReviews.size());
        group.put("remainingCount", remainingCount);
        group.put("bonusPerReview", assignment.getPeerReviewBonusPerReview());
        group.put("bonusCap", assignment.getPeerReviewBonusCap());
        group.put("bonusEarned", enrollment != null ? enrollment.getPeerReviewBonus() : 0);
        group.put("openAt", assignment.getPeerReviewOpenAt());
        group.put("closeAt", assignment.getPeerReviewCloseAt());
        group.put("reviews", reviews);
        return group;
    }

    private List<Submission> getAssignedTargets(Assignment assignment, User reviewer) {
        List<Submission> eligibleTargets = submissionRepository.findByAssignment(assignment).stream()
            .filter(sub -> sub.getStatus() != AssignmentStatus.PENDING)
            .filter(sub -> !sub.getStudent().getId().equals(reviewer.getId()))
            .sorted(Comparator.comparing(Submission::getId))
            .toList();

        List<PeerReview> existing = peerReviewRepository.findByAssignmentAndReviewer(assignment, reviewer);
        Set<Long> reviewedTargetIds = new LinkedHashSet<>();
        for (PeerReview review : existing) {
            reviewedTargetIds.add(review.getTargetSubmission().getId());
        }

        if (eligibleTargets.isEmpty()) {
            return List.of();
        }

        List<Submission> selectable = new ArrayList<>();
        for (Submission candidate : eligibleTargets) {
            if (reviewedTargetIds.contains(candidate.getId())) {
                continue;
            }
            selectable.add(candidate);
        }
        return selectable;
    }

    private Map<String, Object> buildCandidateTarget(Submission submission) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("submissionId", submission.getId());
        item.put("studentId", submission.getStudent().getId());
        item.put("studentName", submission.getStudent().getDisplayName() != null && !submission.getStudent().getDisplayName().isBlank()
            ? submission.getStudent().getDisplayName()
            : submission.getStudent().getUsername());
        item.put("content", submission.getContent() == null ? "" : submission.getContent());
        item.put("preview", buildPreviewText(submission));
        item.put("filePaths", submission.getFilePaths());
        item.put("submittedAt", submission.getSubmittedAt());
        return item;
    }

    private boolean isPeerReviewActive(Assignment assignment, LocalDateTime now) {
        if (!assignment.isPeerReviewEnabled()) {
            return false;
        }
        if (assignment.getPeerReviewOpenAt() != null && now.isBefore(assignment.getPeerReviewOpenAt())) {
            return false;
        }
        return assignment.getPeerReviewCloseAt() == null || !now.isAfter(assignment.getPeerReviewCloseAt());
    }

    private void ensurePeerReviewOpen(Assignment assignment) {
        if (!isPeerReviewActive(assignment, LocalDateTime.now())) {
            throw new RuntimeException("互评窗口未开启或已关闭");
        }
    }

    private String buildPreviewText(Submission submission) {
        String content = submission.getContent();
        if (content != null && !content.isBlank()) {
            return content.length() > 120 ? content.substring(0, 120) + "..." : content;
        }
        if (submission.getFileName() != null && !submission.getFileName().isBlank()) {
            return "附件：" + submission.getFileName();
        }
        if (submission.getFilePaths() != null && !submission.getFilePaths().isBlank()) {
            return "附件：" + submission.getFilePaths();
        }
        return "未填写文字内容";
    }

    private boolean isReasonableReview(Integer rating, String comment) {
        return rating != null && rating >= 1 && rating <= 5 && comment != null && comment.trim().length() >= 10;
    }

    private void recalculateEnrollmentScore(Enrollment enrollment) {
        double base = enrollment.getBaseScore();
        if (base < 0 && enrollment.getScore() >= 0) {
            base = enrollment.getScore();
            enrollment.setBaseScore(base);
        }
        if (base >= 0) {
            enrollment.setScore(base + enrollment.getPeerReviewBonus());
        } else {
            enrollment.setScore(enrollment.getPeerReviewBonus());
        }
    }

    private boolean sameDaySchedule(String a, String b) {
        return a != null && b != null && a.split(" ")[0].equalsIgnoreCase(b.split(" ")[0]);
    }
}
