package com.sms.service;

import com.sms.model.*;
import com.sms.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final NotificationService notificationService;

    public List<Course> getMyCourses(User teacher) {
        return courseRepository.findByTeacher(teacher);
    }

    @Transactional
    public Course createCourse(Course course, User teacher) {
        course.setTeacher(teacher);
        return courseRepository.save(course);
    }

    @Transactional
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
        assignment.setCourse(course);
        Assignment saved = assignmentRepository.save(assignment);

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
    public Submission gradeSubmission(Long submissionId, Double score, String comment) {
        if (score == null || score < 0 || score > 100) {
            throw new RuntimeException("成绩必须在 0 到 100 之间");
        }
        Submission sub = submissionRepository.findById(submissionId)
            .orElseThrow(() -> new RuntimeException("提交记录不存在"));
        sub.setScore(score);
        sub.setTeacherComment(comment);
        sub.setStatus(AssignmentStatus.GRADED);
        sub.setGradedAt(java.time.LocalDateTime.now());

        var assignment = sub.getAssignment();
        enrollmentRepository.findByStudentAndCourse(sub.getStudent(), assignment.getCourse())
            .ifPresent(e -> {
                e.setScore(score);
                enrollmentRepository.save(e);
            });

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
}
