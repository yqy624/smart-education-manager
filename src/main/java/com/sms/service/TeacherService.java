package com.sms.service;

import com.sms.dto.ActivitySummary;
import com.sms.dto.GradeSubmissionRequest;
import com.sms.model.*;
import com.sms.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TeacherService {

    private final CourseRepository courseRepository;
    private final AssignmentRepository assignmentRepository;
    private final SubmissionRepository submissionRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final PeerReviewRepository peerReviewRepository;
    private final NotificationService notificationService;
    private final TeacherCommentMemoryService teacherCommentMemoryService;
    private final ObjectProvider<FileStorageService> fileStorageServiceProvider;
    private final PublishedActivityRepository publishedActivityRepository;

    public Map<String, Object> getDashboard(User teacher) {
        List<Course> courses = getMyCourses(teacher);
        int totalStudents = courses.stream().mapToInt(course -> course.getEnrolledCount()).sum();
        long peerEnabledCount = courses.stream()
            .flatMap(course -> assignmentRepository.findByCourse(course).stream())
            .filter(Assignment::isPeerReviewEnabled)
            .count();
        long activeCourses = courses.stream().filter(course -> course.getEnrolledCount() > 0).count();
        List<Map<String, Object>> courseTrend = courses.stream().map(course -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", course.getName());
            item.put("value", course.getEnrolledCount());
            return item;
        }).toList();
        List<ActivitySummary> recentActivities = publishedActivityRepository.findTop5ByStatusOrderByPublishedAtDesc(PublishedActivityStatus.PUBLISHED).stream()
            .filter(activity -> activity.getAudience() == PublishedActivityAudience.ALL || activity.getAudience() == PublishedActivityAudience.TEACHERS)
            .limit(5)
            .map(activity -> new ActivitySummary(activity.getId(), activity.getTitle(), activity.getContent(), activity.getAudience().name(), activity.getLink(), activity.getStatus().name(), activity.getPublishedAt(), activity.getCreatedAt()))
            .toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalCourses", courses.size());
        result.put("totalStudents", totalStudents);
        result.put("peerEnabledCount", peerEnabledCount);
        result.put("activeCourses", activeCourses);
        result.put("courseTrend", courseTrend);
        result.put("recentActivities", recentActivities);
        return result;
    }

    public List<Course> getMyCourses(User teacher) {
        return courseRepository.findByTeacher(teacher);
    }

    @Transactional
    @CacheEvict(cacheNames = {"visibleCourses", "adminDashboardStats"}, allEntries = true)
    public Course createCourse(Course course, User teacher) {
        course.setTeacher(teacher);
        return courseRepository.save(course);
    }

    @Transactional
    @CacheEvict(cacheNames = {"visibleCourses", "adminDashboardStats"}, allEntries = true)
    public Course updateCourse(Long courseId, Course input, User teacher) {
        Course course = courseRepository.findById(courseId)
            .orElseThrow(() -> new RuntimeException("课程不存在"));
        if (teacher.getRole() != RoleType.ADMIN && !course.getTeacher().getId().equals(teacher.getId())) {
            throw new RuntimeException("无权编辑该课程");
        }
        course.setName(input.getName());
        course.setDescription(input.getDescription());
        course.setSchedule(input.getSchedule());
        course.setCredits(input.getCredits());
        course.setMaxStudents(input.getMaxStudents());
        course.setCategory(input.getCategory());
        return courseRepository.save(course);
    }

    @Transactional
    @CacheEvict(cacheNames = {"visibleCourses", "adminDashboardStats"}, allEntries = true)
    public void deleteCourse(Long courseId) {
        courseRepository.deleteById(courseId);
    }

    public List<User> getCourseStudents(Long courseId) {
        Course course = courseRepository.findById(courseId)
            .orElseThrow(() -> new RuntimeException("课程不存在"));
        return enrollmentRepository.findByCourse(course).stream()
            .map(Enrollment::getStudent)
            .toList();
    }

    @Transactional
    public Assignment createAssignment(Assignment assignment, User teacher) {
        assignment.setTeacher(teacher);
        if (assignment.getCourse() == null || assignment.getCourse().getId() == null) {
            throw new RuntimeException("课程不能为空");
        }
        Course course = courseRepository.findById(assignment.getCourse().getId())
            .orElseThrow(() -> new RuntimeException("课程不存在"));
        if (teacher.getRole() != RoleType.ADMIN && (course.getTeacher() == null || !course.getTeacher().getId().equals(teacher.getId()))) {
            throw new RuntimeException("无权在该课程下发布作业");
        }
        assignment.setCourse(course);
        Assignment saved = assignmentRepository.save(assignment);
        bindAssignmentFiles(saved);

        List<String> students = enrollmentRepository.findByCourse(course).stream()
            .map(e -> e.getStudent().getUsername())
            .toList();
        notificationService.notifyAll(students, "ASSIGNMENT",
            "新作业：" + saved.getTitle(),
            "课程《" + course.getName() + "》发布了新作业，请及时完成。",
            "assignment:" + saved.getId());
        return saved;
    }

    public List<Assignment> getCourseAssignments(Long courseId) {
        Course course = courseRepository.findById(courseId)
            .orElseThrow(() -> new RuntimeException("课程不存在"));
        return assignmentRepository.findByCourse(course);
    }

    public List<Submission> getPendingSubmissions(Long assignmentId) {
        Assignment assignment = assignmentRepository.findById(assignmentId)
            .orElseThrow(() -> new RuntimeException("作业不存在"));
        return submissionRepository.findByAssignment(assignment);
    }

    @Transactional
    public Submission gradeSubmission(Long submissionId, GradeSubmissionRequest request, User teacher) {
        Double score = request == null ? null : request.getScore();
        if (score == null || score < 0 || score > 100) {
            throw new RuntimeException("成绩必须在 0 到 100 之间");
        }
        Submission sub = submissionRepository.findById(submissionId)
            .orElseThrow(() -> new RuntimeException("提交记录不存在"));
        Assignment assignment = sub.getAssignment();
        if (teacher.getRole() != RoleType.ADMIN && !assignment.getTeacher().getId().equals(teacher.getId())) {
            throw new RuntimeException("无权评分该提交记录");
        }

        String comment = request.getComment() == null ? "" : request.getComment().trim();
        sub.setScore(score);
        sub.setTeacherComment(comment);
        sub.setStatus(AssignmentStatus.GRADED);
        sub.setGradedAt(LocalDateTime.now());

        enrollmentRepository.findByStudentAndCourse(sub.getStudent(), assignment.getCourse())
            .ifPresent(e -> {
                e.setBaseScore(score);
                if (e.getPeerReviewBonus() < 0) {
                    e.setPeerReviewBonus(0);
                }
                e.setScore(score + e.getPeerReviewBonus());
                enrollmentRepository.save(e);
            });

        if (teacher.getRole() == RoleType.TEACHER && !comment.isBlank()) {
            teacherCommentMemoryService.recordUsage(teacher, sub, score, comment, request.getQuickCommentId());
        }

        notificationService.notify(sub.getStudent().getUsername(), "GRADE",
            "作业已评分：" + assignment.getTitle(),
            "你的作业《" + assignment.getTitle() + "》已批改，得分 " + score + " 分。",
            "grade:" + sub.getId());

        return submissionRepository.save(sub);
    }

    public Map<String, Object> getAssignmentAnalysis(Long assignmentId) {
        Assignment assignment = assignmentRepository.findById(assignmentId)
            .orElseThrow(() -> new RuntimeException("作业不存在"));

        List<Double> scores = submissionRepository.findByAssignment(assignment).stream()
            .filter(s -> s.getStatus() == AssignmentStatus.GRADED && s.getScore() != null)
            .map(Submission::getScore)
            .toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("assignmentTitle", assignment.getTitle());
        result.put("buckets", List.of("0-59", "60-69", "70-79", "80-89", "90-100"));

        int total = scores.size();
        result.put("total", total);

        if (total == 0) {
            result.put("distribution", List.of(0, 0, 0, 0, 0));
            result.put("passCount", 0);
            result.put("passRate", 0.0);
            result.put("average", 0.0);
            result.put("max", 0.0);
            result.put("min", 0.0);
            return result;
        }

        int[] dist = new int[5];
        int passCount = 0;
        double sum = 0, max = Double.MIN_VALUE, min = Double.MAX_VALUE;
        for (double s : scores) {
            sum += s;
            max = Math.max(max, s);
            min = Math.min(min, s);
            if (s >= 60) passCount++;
            int idx = s < 60 ? 0 : s < 70 ? 1 : s < 80 ? 2 : s < 90 ? 3 : 4;
            dist[idx]++;
        }

        result.put("distribution", List.of(dist[0], dist[1], dist[2], dist[3], dist[4]));
        result.put("passCount", passCount);
        result.put("passRate", Math.round(passCount * 1000.0 / total) / 10.0);
        result.put("average", Math.round(sum / total * 10.0) / 10.0);
        result.put("max", max);
        result.put("min", min);
        return result;
    }

    @Transactional
    public Assignment configurePeerReview(Long assignmentId, Map<String, Object> body, User teacher) {
        Assignment assignment = assignmentRepository.findById(assignmentId)
            .orElseThrow(() -> new RuntimeException("作业不存在"));
        if (teacher.getRole() != RoleType.ADMIN && !assignment.getTeacher().getId().equals(teacher.getId())) {
            throw new RuntimeException("无权修改该作业");
        }
        assignment.setPeerReviewEnabled(Boolean.TRUE.equals(body.get("peerReviewEnabled")));
        assignment.setPeerReviewOpenAt(parseDateTime(body.get("peerReviewOpenAt")));
        assignment.setPeerReviewCloseAt(parseDateTime(body.get("peerReviewCloseAt")));
        assignment.setPeerReviewRequiredCount(toInt(body.get("peerReviewRequiredCount"), 1));
        assignment.setPeerReviewBonusPerReview(toDouble(body.get("peerReviewBonusPerReview"), 1.0));
        assignment.setPeerReviewBonusCap(toDouble(body.get("peerReviewBonusCap"), 1.0));
        assignment.setPeerReviewPrompt((String) body.getOrDefault("peerReviewPrompt", ""));
        return assignmentRepository.save(assignment);
    }

    public Map<String, Object> getPeerReviewOverview(Long assignmentId) {
        Assignment assignment = assignmentRepository.findById(assignmentId)
            .orElseThrow(() -> new RuntimeException("作业不存在"));
        List<PeerReview> reviews = peerReviewRepository.findByAssignment(assignment);
        List<Map<String, Object>> items = new ArrayList<>();
        for (PeerReview review : reviews) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", review.getId());
            item.put("reviewer", review.getReviewer().getDisplayName() != null ? review.getReviewer().getDisplayName() : review.getReviewer().getUsername());
            item.put("status", review.getStatus());
            item.put("rating", review.getRating());
            item.put("submittedAt", review.getSubmittedAt());
            item.put("bonusGrantedAt", review.getBonusGrantedAt());
            items.add(item);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("assignmentId", assignmentId);
        result.put("enabled", assignment.isPeerReviewEnabled());
        result.put("openAt", assignment.getPeerReviewOpenAt());
        result.put("closeAt", assignment.getPeerReviewCloseAt());
        result.put("requiredCount", assignment.getPeerReviewRequiredCount());
        result.put("bonusPerReview", assignment.getPeerReviewBonusPerReview());
        result.put("bonusCap", assignment.getPeerReviewBonusCap());
        result.put("prompt", assignment.getPeerReviewPrompt());
        result.put("totalReviews", reviews.size());
        result.put("grantedCount", reviews.stream().filter(r -> r.getStatus() == PeerReviewStatus.BONUS_GRANTED).count());
        result.put("reviews", items);
        return result;
    }

    private void bindAssignmentFiles(Assignment assignment) {
        if (assignment.getAttachmentPaths() == null || assignment.getAttachmentPaths().isBlank()) {
            return;
        }
        String[] entries = assignment.getAttachmentPaths().split(",");
        for (String entry : entries) {
            String item = entry == null ? "" : entry.trim();
            if (item.isBlank()) {
                continue;
            }
            String storagePath = item.split("::", 2)[0];
            fileStorageServiceProvider.getObject().bindStoredFile(
                storagePath,
                StoredFileCategory.ASSIGNMENT_ATTACHMENT,
                assignment.getCourse().getId(),
                assignment.getId(),
                null
            );
        }
    }

    private LocalDateTime parseDateTime(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        if (text.length() == 16) {
            text = text + ":00";
        }
        return LocalDateTime.parse(text.replace(' ', 'T'));
    }

    private int toInt(Object value, int defaultValue) {
        if (value == null || String.valueOf(value).isBlank()) {
            return defaultValue;
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private double toDouble(Object value, double defaultValue) {
        if (value == null || String.valueOf(value).isBlank()) {
            return defaultValue;
        }
        return Double.parseDouble(String.valueOf(value));
    }
}
