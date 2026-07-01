package com.sms.service;

import com.sms.model.*;
import com.sms.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class StudentService {

    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final AssignmentRepository assignmentRepository;
    private final SubmissionRepository submissionRepository;

    public List<Course> getAllCourses() { return courseRepository.findByVisibleTrue(); }
    public List<Course> getMyCourses(User student) { return enrollmentRepository.findByStudent(student).stream().map(Enrollment::getCourse).toList(); }

    @Transactional
    public void enroll(Long courseId, User student) {
        Course course = courseRepository.findById(courseId).orElseThrow(() -> new RuntimeException("课程不存在"));
        if (!course.isVisible()) throw new RuntimeException("课程已下架");
        if (enrollmentRepository.existsByStudentAndCourse(student, course)) throw new RuntimeException("已经选过该课程");
        if (course.getEnrolledCount() >= course.getMaxStudents()) throw new RuntimeException("课程已满");
        List<Enrollment> existing = enrollmentRepository.findByStudent(student);
        boolean conflict = existing.stream().anyMatch(e -> sameDaySchedule(e.getCourse().getSchedule(), course.getSchedule()));
        if (conflict) throw new RuntimeException("选课时间冲突");
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
    public Submission submitAssignment(Long assignmentId, User student, String content) { return submitAssignment(assignmentId, student, content, null); }

    @Transactional
    public Submission submitAssignment(Long assignmentId, User student, String content, String filePaths) {
        Assignment assignment = assignmentRepository.findById(assignmentId).orElseThrow(() -> new RuntimeException("作业不存在"));
        Submission existing = submissionRepository.findByAssignmentAndStudent(assignment, student).orElse(null);
        if (existing == null) existing = Submission.builder().assignment(assignment).student(student).build();
        existing.setContent(content);
        existing.setFilePaths(filePaths);
        return submissionRepository.save(existing);
    }

    public List<Map<String, Object>> getMyGrades(User student) {
        List<Map<String, Object>> grades = new ArrayList<>();
        for (Submission submission : submissionRepository.findByStudent(student)) {
            Assignment assignment = submission.getAssignment();
            Course course = assignment.getCourse();
            Map<String, Object> item = new java.util.LinkedHashMap<>();
            item.put("courseId", course.getId());
            item.put("courseName", course.getName());
            item.put("assignmentId", assignment.getId());
            item.put("assignmentTitle", assignment.getTitle());
            item.put("status", submission.getStatus());
            item.put("score", submission.getScore());
            item.put("teacherComment", submission.getTeacherComment());
            item.put("content", submission.getContent());
            item.put("filePaths", submission.getFilePaths());
            grades.add(item);
        }
        return grades;
    }

    public Double getCourseAverageScore(Long courseId) {
        Course course = courseRepository.findById(courseId).orElseThrow(() -> new RuntimeException("课程不存在"));
        return enrollmentRepository.findByCourse(course).stream()
            .filter(e -> e.getScore() >= 0)
            .mapToDouble(Enrollment::getScore)
            .average()
            .orElse(-1);
    }

    private boolean sameDaySchedule(String a, String b) { return a != null && b != null && a.split(" ")[0].equalsIgnoreCase(b.split(" ")[0]); }
}
