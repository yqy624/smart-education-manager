package com.sms.controller;

import com.sms.dto.ApiResponse;
import com.sms.dto.PageResponse;
import com.sms.dto.UserCreateRequest;
import com.sms.dto.admin.AdminAuditActor;
import com.sms.dto.admin.AdminBatchCourseActionRequest;
import com.sms.dto.admin.AdminActivityRequest;
import com.sms.dto.admin.AdminCourseEnrollmentAdjustRequest;
import com.sms.dto.admin.AdminCourseUpdateRequest;
import com.sms.model.User;
import com.sms.service.AdminService;
import com.sms.service.AuthService;
import com.sms.service.ExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Admin", description = "管理员侧用户、课程、审计、监控与导出接口")
@SecurityRequirement(name = "bearerAuth")
public class AdminController {

    private final AdminService adminService;
    private final AuthService authService;
    private final ExportService exportService;

    @Operation(summary = "查看全部用户", description = "返回系统中的全部用户列表。")
    @GetMapping("/users")
    public ApiResponse<?> listUsers() {
        return ApiResponse.ok(adminService.getAllUsers());
    }

    @Operation(summary = "分页查询用户", description = "支持按关键词、角色、启用状态分页查询用户。")
    @GetMapping("/users/page")
    public ApiResponse<?> pageUsers(@Parameter(description = "页码，从 0 开始") @RequestParam(defaultValue = "0") int page,
                                    @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") int size,
                                    @Parameter(description = "用户名或显示名关键词") @RequestParam(required = false) String keyword,
                                    @Parameter(description = "角色筛选，如 ADMIN / TEACHER / STUDENT") @RequestParam(required = false) String role,
                                    @Parameter(description = "是否启用") @RequestParam(required = false) Boolean enabled) {
        return ApiResponse.ok(PageResponse.from(adminService.searchUsers(keyword, role, enabled, page, size)));
    }

    @Operation(summary = "查看单个用户", description = "按用户 ID 获取用户详情。")
    @GetMapping("/users/{id}")
    public ApiResponse<?> getUser(@Parameter(description = "用户 ID") @PathVariable Long id) {
        return adminService.getUserById(id)
            .map(ApiResponse::ok)
            .orElse(ApiResponse.error("用户不存在"));
    }

    @Operation(summary = "创建用户", description = "由管理员创建新用户账号。")
    @PostMapping("/users")
    public ApiResponse<?> createUser(
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "新用户信息",
            required = true,
            content = @Content(examples = @ExampleObject(value = """
                {
                  "username": "student2",
                  "password": "student123",
                  "displayName": "王同学",
                  "email": "student2@sms.com",
                  "role": "STUDENT"
                }
                """))
        )
        @RequestBody UserCreateRequest req) {
        try {
            User user = authService.register(req);
            return ApiResponse.ok("创建用户成功", user);
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    @Operation(summary = "切换用户启用状态", description = "启用或禁用指定用户账号。")
    @PutMapping("/users/{id}/toggle")
    public ApiResponse<?> toggleUser(@Parameter(description = "用户 ID") @PathVariable Long id) {
        adminService.toggleUserEnabled(id);
        return ApiResponse.ok("状态已变更");
    }

    @Operation(summary = "删除用户", description = "删除指定用户账号。")
    @DeleteMapping("/users/{id}")
    public ApiResponse<?> deleteUser(@Parameter(description = "用户 ID") @PathVariable Long id) {
        adminService.deleteUser(id);
        return ApiResponse.ok("用户已删除");
    }

    @Operation(summary = "重置用户密码", description = "按用户 ID 重置密码。")
    @PutMapping("/users/{id}/password")
    public ApiResponse<?> resetPassword(
        @Parameter(description = "用户 ID") @PathVariable Long id,
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "新密码",
            required = true,
            content = @Content(examples = @ExampleObject(value = """
                {
                  "password": "newPassword123"
                }
                """))
        )
        @RequestBody Map<String, String> payload) {
        String newPassword = payload.get("password");
        if (newPassword == null || newPassword.trim().isEmpty()) {
            return ApiResponse.error("密码不能为空");
        }
        adminService.resetUserPassword(id, newPassword);
        return ApiResponse.ok("密码已重置");
    }

    @Operation(summary = "查看管理首页统计", description = "返回管理员首页的关键统计指标。")
    @GetMapping("/dashboard")
    public ApiResponse<?> dashboard() {
        return ApiResponse.ok(adminService.getDashboardStats());
    }

    @Operation(summary = "查看活动列表", description = "返回管理员活动面板中的最新活动列表。")
    @GetMapping("/activities")
    public ApiResponse<?> activities() {
        return ApiResponse.ok(adminService.listActivities());
    }

    @Operation(summary = "新建活动", description = "创建并发布一条首页活动。")
    @PostMapping("/activities")
    public ApiResponse<?> createActivity(@RequestBody AdminActivityRequest request,
                                         @Parameter(hidden = true) Authentication authentication,
                                         @Parameter(hidden = true) HttpServletRequest httpRequest) {
        adminService.createActivity(request, buildActor(authentication, httpRequest));
        return ApiResponse.ok("活动已发布");
    }

    @Operation(summary = "编辑活动", description = "修改已存在的活动内容。")
    @PutMapping("/activities/{id}")
    public ApiResponse<?> updateActivity(@PathVariable Long id,
                                         @RequestBody AdminActivityRequest request,
                                         @Parameter(hidden = true) Authentication authentication,
                                         @Parameter(hidden = true) HttpServletRequest httpRequest) {
        adminService.updateActivity(id, request, buildActor(authentication, httpRequest));
        return ApiResponse.ok("活动已更新");
    }

    @Operation(summary = "删除活动", description = "删除指定活动。")
    @DeleteMapping("/activities/{id}")
    public ApiResponse<?> deleteActivity(@PathVariable Long id,
                                         @Parameter(hidden = true) Authentication authentication,
                                         @Parameter(hidden = true) HttpServletRequest httpRequest) {
        adminService.deleteActivity(id, buildActor(authentication, httpRequest));
        return ApiResponse.ok("活动已删除");
    }

    @Operation(summary = "重新发布活动", description = "将活动重新推送给目标受众。")
    @PostMapping("/activities/{id}/republish")
    public ApiResponse<?> republishActivity(@PathVariable Long id,
                                            @Parameter(hidden = true) Authentication authentication,
                                            @Parameter(hidden = true) HttpServletRequest httpRequest) {
        adminService.republishActivity(id, buildActor(authentication, httpRequest));
        return ApiResponse.ok("活动已重新发布");
    }

    @Operation(summary = "查看系统监控信息", description = "返回系统运行状态和基础监控数据。")
    @GetMapping("/monitor")
    public ApiResponse<?> monitor() {
        return ApiResponse.ok(adminService.getSystemMonitor());
    }

    @Operation(summary = "导出用户列表", description = "将用户数据导出为 CSV 文件。")
    @io.swagger.v3.oas.annotations.responses.ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "导出成功",
            content = @Content(mediaType = "text/csv"))
    })
    @GetMapping("/users/export")
    public ResponseEntity<byte[]> exportUsers() {
        byte[] data = exportService.exportUsersCsv();
        return csvResponse(data, "users.csv");
    }

    @Operation(summary = "分页查询审计日志", description = "支持按关键词分页查询系统审计日志。")
    @GetMapping("/logs/page")
    public ApiResponse<?> pageLogs(@Parameter(description = "页码，从 0 开始") @RequestParam(defaultValue = "0") int page,
                                   @Parameter(description = "每页条数") @RequestParam(defaultValue = "15") int size,
                                   @Parameter(description = "日志关键词") @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(PageResponse.from(adminService.searchLogs(keyword, page, size)));
    }

    @Operation(summary = "分页查询课程", description = "按关键词分页查询课程列表。")
    @GetMapping("/courses/page")
    public ApiResponse<?> pageCourses(@Parameter(description = "页码，从 0 开始") @RequestParam(defaultValue = "0") int page,
                                      @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") int size,
                                      @Parameter(description = "课程关键词") @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(PageResponse.from(adminService.searchCourses(keyword, page, size)));
    }

    @Operation(summary = "查看教师选项", description = "返回可分配课程的教师选项列表。")
    @GetMapping("/course-options/teachers")
    public ApiResponse<?> teacherOptions() {
        return ApiResponse.ok(adminService.getTeacherOptions());
    }

    @Operation(summary = "更新课程信息", description = "管理员更新课程基本信息。")
    @PutMapping("/courses/{id}")
    public ApiResponse<?> updateCourse(@Parameter(description = "课程 ID") @PathVariable Long id,
                                       @RequestBody AdminCourseUpdateRequest request,
                                       @Parameter(hidden = true) Authentication authentication,
                                       @Parameter(hidden = true) HttpServletRequest httpRequest) {
        return ApiResponse.ok("课程更新成功", adminService.updateCourse(id, request, buildActor(authentication, httpRequest)));
    }

    @Operation(summary = "查看课程选课明细", description = "返回指定课程下的学生选课详情。")
    @GetMapping("/courses/{id}/enrollments")
    public ApiResponse<?> getCourseEnrollments(@Parameter(description = "课程 ID") @PathVariable Long id) {
        return ApiResponse.ok(adminService.getCourseEnrollmentDetails(id));
    }

    @Operation(summary = "调整课程选课名单", description = "管理员调整指定课程的学生选课关系。")
    @PutMapping("/courses/{id}/enrollments")
    public ApiResponse<?> adjustCourseEnrollments(@Parameter(description = "课程 ID") @PathVariable Long id,
                                                  @RequestBody AdminCourseEnrollmentAdjustRequest request,
                                                  @Parameter(hidden = true) Authentication authentication,
                                                  @Parameter(hidden = true) HttpServletRequest httpRequest) {
        return ApiResponse.ok("选课调整成功", adminService.adjustCourseEnrollments(id, request, buildActor(authentication, httpRequest)));
    }

    @Operation(summary = "切换课程可见状态", description = "管理员上架或下架指定课程。")
    @PutMapping("/courses/{id}/visibility")
    public ApiResponse<?> toggleCourseVisibility(@Parameter(description = "课程 ID") @PathVariable Long id,
                                                 @Parameter(hidden = true) Authentication authentication,
                                                 @Parameter(hidden = true) HttpServletRequest httpRequest) {
        return ApiResponse.ok("课程状态已更新", adminService.toggleCourseVisibility(id, buildActor(authentication, httpRequest)));
    }

    @Operation(summary = "删除课程", description = "管理员删除指定课程。")
    @DeleteMapping("/courses/{id}")
    public ApiResponse<?> deleteCourse(@Parameter(description = "课程 ID") @PathVariable Long id,
                                       @Parameter(hidden = true) Authentication authentication,
                                       @Parameter(hidden = true) HttpServletRequest httpRequest) {
        adminService.deleteCourse(id, buildActor(authentication, httpRequest));
        return ApiResponse.ok("课程已删除");
    }

    @Operation(summary = "批量下架课程", description = "按课程 ID 列表批量下架课程。")
    @PutMapping("/courses/batch-hide")
    public ApiResponse<?> batchHideCourses(@RequestBody AdminBatchCourseActionRequest request,
                                           @Parameter(hidden = true) Authentication authentication,
                                           @Parameter(hidden = true) HttpServletRequest httpRequest) {
        adminService.batchHideCourses(request.getCourseIds(), buildActor(authentication, httpRequest));
        return ApiResponse.ok("批量下架成功");
    }

    @Operation(summary = "批量删除课程", description = "按课程 ID 列表批量删除课程。")
    @DeleteMapping("/courses/batch-delete")
    public ApiResponse<?> batchDeleteCourses(@RequestBody AdminBatchCourseActionRequest request,
                                             @Parameter(hidden = true) Authentication authentication,
                                             @Parameter(hidden = true) HttpServletRequest httpRequest) {
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
