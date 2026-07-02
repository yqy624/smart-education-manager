package com.sms.controller;

import com.sms.dto.ApiResponse;
import com.sms.model.Assignment;
import com.sms.model.Course;
import com.sms.model.User;
import com.sms.repository.UserRepository;
import com.sms.service.ExportService;
import com.sms.service.TeacherService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/api/teacher")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
public class TeacherController {

    private final TeacherService teacherService;
    private final UserRepository userRepository;
    private final ExportService exportService;

    private User getCurrentUser(Authentication auth) {
        return userRepository.findByUsername(auth.getName())
            .orElseThrow(() -> new RuntimeException("用户不存在"));
    }

    // ========= 课程管理 =========
    @GetMapping("/courses")
    public ApiResponse<?> myCourses(Authentication auth) {
        return ApiResponse.ok(teacherService.getMyCourses(getCurrentUser(auth)));
    }

    @PostMapping("/courses")
    public ApiResponse<?> createCourse(@RequestBody Course course, Authentication auth) {
        return ApiResponse.ok("课程创建成功",
            teacherService.createCourse(course, getCurrentUser(auth)));
    }

    @PutMapping("/courses/{id}")
    public ApiResponse<?> updateCourse(@PathVariable Long id, @RequestBody Course course, Authentication auth) {
        return ApiResponse.ok("课程更新成功", teacherService.updateCourse(id, course, getCurrentUser(auth)));
    }

    @DeleteMapping("/courses/{id}")
    public ApiResponse<?> deleteCourse(@PathVariable Long id) {
        teacherService.deleteCourse(id);
        return ApiResponse.ok("课程已删除");
    }

    @GetMapping("/courses/{id}/students")
    public ApiResponse<?> courseStudents(@PathVariable Long id) {
        return ApiResponse.ok(teacherService.getCourseStudents(id));
    }

    // ========= 作业管理 =========
    @PostMapping("/assignments")
    public ApiResponse<?> createAssignment(@RequestBody Assignment assignment,
                                            Authentication auth) {
        return ApiResponse.ok("作业发布成功",
            teacherService.createAssignment(assignment, getCurrentUser(auth)));
    }

    @GetMapping("/courses/{id}/assignments")
    public ApiResponse<?> courseAssignments(@PathVariable Long id) {
        return ApiResponse.ok(teacherService.getCourseAssignments(id));
    }

    @PostMapping("/assignments/{id}/peer-review")
    public ApiResponse<?> configurePeerReview(@PathVariable Long id,
                                              @RequestBody Map<String, Object> body,
                                              Authentication auth) {
        try {
            return ApiResponse.ok("互评配置已保存",
                teacherService.configurePeerReview(id, body, getCurrentUser(auth)));
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    @GetMapping("/assignments/{id}/peer-review")
    public ApiResponse<?> peerReviewOverview(@PathVariable Long id) {
        return ApiResponse.ok(teacherService.getPeerReviewOverview(id));
    }

    // ========= 成绩录入 =========
    @GetMapping("/assignments/{id}/submissions")
    public ApiResponse<?> pendingSubmissions(@PathVariable Long id) {
        return ApiResponse.ok(teacherService.getPendingSubmissions(id));
    }

    @PostMapping("/submissions/{id}/grade")
    public ApiResponse<?> gradeSubmission(@PathVariable Long id,
                                           @RequestBody Map<String, Object> body) {
        Double score = Double.parseDouble(body.get("score").toString());
        String comment = (String) body.getOrDefault("comment", "");
        return ApiResponse.ok("评分成功",
            teacherService.gradeSubmission(id, score, comment));
    }

    // ========= 成绩分析 =========
    @GetMapping("/assignments/{id}/analysis")
    public ApiResponse<?> assignmentAnalysis(@PathVariable Long id) {
        return ApiResponse.ok(teacherService.getAssignmentAnalysis(id));
    }

    // ========= 数据导出 =========
    @GetMapping("/courses/{id}/grades/export")
    public ResponseEntity<byte[]> exportCourseGrades(@PathVariable Long id) {
        byte[] data = exportService.exportCourseGradesCsv(id);
        String filename = "course_" + id + "_grades.csv";
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
            .header(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"" + encoded + "\"; filename*=UTF-8''" + encoded)
            .body(data);
    }
}
