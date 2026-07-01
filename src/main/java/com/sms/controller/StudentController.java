package com.sms.controller;

import com.sms.dto.ApiResponse;
import com.sms.model.User;
import com.sms.repository.UserRepository;
import com.sms.service.StudentService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/student")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'TEACHER', 'STUDENT')")
public class StudentController {

    private final StudentService studentService;
    private final UserRepository userRepository;

    private User getCurrentUser(Authentication auth) {
        return userRepository.findByUsername(auth.getName())
            .orElseThrow(() -> new RuntimeException("用户不存在"));
    }

    // ========= 选课 =========
    @GetMapping("/courses")
    public ApiResponse<?> allCourses() {
        return ApiResponse.ok(studentService.getAllCourses());
    }

    @GetMapping("/my-courses")
    public ApiResponse<?> myCourses(Authentication auth) {
        return ApiResponse.ok(studentService.getMyCourses(getCurrentUser(auth)));
    }

    @PostMapping("/enroll/{courseId}")
    public ApiResponse<?> enroll(@PathVariable Long courseId, Authentication auth) {
        try {
            studentService.enroll(courseId, getCurrentUser(auth));
            return ApiResponse.ok("选课成功");
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    @PostMapping("/drop/{courseId}")
    public ApiResponse<?> drop(@PathVariable Long courseId, Authentication auth) {
        studentService.drop(courseId, getCurrentUser(auth));
        return ApiResponse.ok("退课成功");
    }

    // ========= 作业提交 =========
    @GetMapping("/courses/{id}/assignments")
    public ApiResponse<?> assignments(@PathVariable Long id, Authentication auth) {
        return ApiResponse.ok(studentService.getMyAssignments(id, getCurrentUser(auth)));
    }

    @PostMapping("/assignments/{id}/submit")
    public ApiResponse<?> submit(@PathVariable Long id,
                                  @RequestBody Map<String, String> body,
                                  Authentication auth) {
        String content = body.get("content");
        // 【模块4新增】body 可携带 filePath / fileName（来自 /api/files/upload 的返回），
        // 若有则以 "path::原名" 形式存入 Submission.filePaths，便于下载时还原文件名
        String filePath = body.get("filePath");
        String fileName = body.get("fileName");
        String stored = null;
        if (filePath != null && !filePath.isBlank()) {
            stored = filePath + (fileName != null && !fileName.isBlank() ? "::" + fileName : "");
        }
        return ApiResponse.ok("提交成功",
            studentService.submitAssignment(id, getCurrentUser(auth), content, stored));
    }

    // ========= 成绩 =========
    @GetMapping("/grades")
    public ApiResponse<?> grades(Authentication auth) {
        return ApiResponse.ok(studentService.getMyGrades(getCurrentUser(auth)));
    }

    @GetMapping("/courses/{id}/average")
    public ApiResponse<?> courseAverage(@PathVariable Long id) {
        Double avg = studentService.getCourseAverageScore(id);
        if (avg < 0) return ApiResponse.error("暂无成绩数据");
        return ApiResponse.ok(Map.of("courseId", id, "average", avg));
    }
}
