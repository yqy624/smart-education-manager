package com.sms.controller;

import com.sms.dto.ApiResponse;
import com.sms.dto.PageResponse;
import com.sms.dto.UserCreateRequest;
import com.sms.dto.admin.AdminAuditActor;
import com.sms.dto.admin.AdminBatchCourseActionRequest;
import com.sms.dto.admin.AdminCourseEnrollmentAdjustRequest;
import com.sms.dto.admin.AdminCourseUpdateRequest;
import com.sms.model.User;
import com.sms.service.AdminService;
import com.sms.service.AuthService;
import com.sms.service.ExportService;
import jakarta.servlet.http.HttpServletRequest;
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
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminService adminService;
    private final AuthService authService;
    private final ExportService exportService;

    @GetMapping("/users")
    public ApiResponse<?> listUsers() {
        return ApiResponse.ok(adminService.getAllUsers());
    }

    @GetMapping("/users/page")
    public ApiResponse<?> pageUsers(@RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "10") int size,
                                    @RequestParam(required = false) String keyword,
                                    @RequestParam(required = false) String role,
                                    @RequestParam(required = false) Boolean enabled) {
        return ApiResponse.ok(PageResponse.from(adminService.searchUsers(keyword, role, enabled, page, size)));
    }

    @GetMapping("/users/{id}")
    public ApiResponse<?> getUser(@PathVariable Long id) {
        return adminService.getUserById(id)
            .map(ApiResponse::ok)
            .orElse(ApiResponse.error("用户不存在"));
    }

    @PostMapping("/users")
    public ApiResponse<?> createUser(@RequestBody UserCreateRequest req) {
        try {
            User user = authService.register(req);
            return ApiResponse.ok("创建用户成功", user);
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    @PutMapping("/users/{id}/toggle")
    public ApiResponse<?> toggleUser(@PathVariable Long id) {
        adminService.toggleUserEnabled(id);
        return ApiResponse.ok("状态已变更");
    }

    @DeleteMapping("/users/{id}")
    public ApiResponse<?> deleteUser(@PathVariable Long id) {
        adminService.deleteUser(id);
        return ApiResponse.ok("用户已删除");
    }

    @PutMapping("/users/{id}/password")
    public ApiResponse<?> resetPassword(@PathVariable Long id, @RequestBody Map<String, String> payload) {
        String newPassword = payload.get("password");
        if (newPassword == null || newPassword.trim().isEmpty()) {
            return ApiResponse.error("密码不能为空");
        }
        adminService.resetUserPassword(id, newPassword);
        return ApiResponse.ok("密码已重置");
    }

    @GetMapping("/dashboard")
    public ApiResponse<?> dashboard() {
        return ApiResponse.ok(adminService.getDashboardStats());
    }

    @GetMapping("/monitor")
    public ApiResponse<?> monitor() {
        return ApiResponse.ok(adminService.getSystemMonitor());
    }

    @GetMapping("/users/export")
    public ResponseEntity<byte[]> exportUsers() {
        byte[] data = exportService.exportUsersCsv();
        return csvResponse(data, "users.csv");
    }

    @GetMapping("/logs/page")
    public ApiResponse<?> pageLogs(@RequestParam(defaultValue = "0") int page,
                                   @RequestParam(defaultValue = "15") int size,
                                   @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(PageResponse.from(adminService.searchLogs(keyword, page, size)));
    }

    @GetMapping("/courses/page")
    public ApiResponse<?> pageCourses(@RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "10") int size,
                                      @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(PageResponse.from(adminService.searchCourses(keyword, page, size)));
    }

    @GetMapping("/course-options/teachers")
    public ApiResponse<?> teacherOptions() {
        return ApiResponse.ok(adminService.getTeacherOptions());
    }

    @PutMapping("/courses/{id}")
    public ApiResponse<?> updateCourse(@PathVariable Long id,
                                       @RequestBody AdminCourseUpdateRequest request,
                                       Authentication authentication,
                                       HttpServletRequest httpRequest) {
        return ApiResponse.ok("课程更新成功", adminService.updateCourse(id, request, buildActor(authentication, httpRequest)));
    }

    @GetMapping("/courses/{id}/enrollments")
    public ApiResponse<?> getCourseEnrollments(@PathVariable Long id) {
        return ApiResponse.ok(adminService.getCourseEnrollmentDetails(id));
    }

    @PutMapping("/courses/{id}/enrollments")
    public ApiResponse<?> adjustCourseEnrollments(@PathVariable Long id,
                                                  @RequestBody AdminCourseEnrollmentAdjustRequest request,
                                                  Authentication authentication,
                                                  HttpServletRequest httpRequest) {
        return ApiResponse.ok("选课调整成功", adminService.adjustCourseEnrollments(id, request, buildActor(authentication, httpRequest)));
    }

    @PutMapping("/courses/{id}/visibility")
    public ApiResponse<?> toggleCourseVisibility(@PathVariable Long id,
                                                 Authentication authentication,
                                                 HttpServletRequest httpRequest) {
        return ApiResponse.ok("课程状态已更新", adminService.toggleCourseVisibility(id, buildActor(authentication, httpRequest)));
    }

    @DeleteMapping("/courses/{id}")
    public ApiResponse<?> deleteCourse(@PathVariable Long id,
                                       Authentication authentication,
                                       HttpServletRequest httpRequest) {
        adminService.deleteCourse(id, buildActor(authentication, httpRequest));
        return ApiResponse.ok("课程已删除");
    }

    @PutMapping("/courses/batch-hide")
    public ApiResponse<?> batchHideCourses(@RequestBody AdminBatchCourseActionRequest request,
                                           Authentication authentication,
                                           HttpServletRequest httpRequest) {
        adminService.batchHideCourses(request.getCourseIds(), buildActor(authentication, httpRequest));
        return ApiResponse.ok("批量下架成功");
    }

    @DeleteMapping("/courses/batch-delete")
    public ApiResponse<?> batchDeleteCourses(@RequestBody AdminBatchCourseActionRequest request,
                                             Authentication authentication,
                                             HttpServletRequest httpRequest) {
        adminService.batchDeleteCourses(request.getCourseIds(), buildActor(authentication, httpRequest));
        return ApiResponse.ok("批量删除成功");
    }

    private ResponseEntity<byte[]> csvResponse(byte[] data, String filename) {
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
            .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
            .body(data);
    }

    private AdminAuditActor buildActor(Authentication authentication, HttpServletRequest request) {
        String role = authentication.getAuthorities().stream().findFirst().map(Object::toString).orElse("ROLE_ADMIN");
        return new AdminAuditActor(authentication.getName(), role, request.getRemoteAddr());
    }
}



