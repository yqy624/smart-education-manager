package com.sms.controller;

import com.sms.dto.ApiResponse;
import com.sms.dto.GradeSubmissionRequest;
import com.sms.model.Assignment;
import com.sms.model.Course;
import com.sms.model.User;
import com.sms.repository.UserRepository;
import com.sms.service.ExportService;
import com.sms.service.TeacherService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Teacher", description = "教师侧课程、作业、评分、互评与导出接口")
@SecurityRequirement(name = "bearerAuth")
public class TeacherController {

    private final TeacherService teacherService;
    private final UserRepository userRepository;
    private final ExportService exportService;

    private User getCurrentUser(Authentication auth) {
        return userRepository.findByUsername(auth.getName())
            .orElseThrow(() -> new RuntimeException("用户不存在"));
    }

    @Operation(summary = "查看我的课程", description = "返回当前教师负责的课程列表。")
    @GetMapping("/courses")
    public ApiResponse<?> myCourses(@Parameter(hidden = true) Authentication auth) {
        return ApiResponse.ok(teacherService.getMyCourses(getCurrentUser(auth)));
    }

    @Operation(summary = "创建课程", description = "教师创建一门新课程。")
    @PostMapping("/courses")
    public ApiResponse<?> createCourse(
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "课程基础信息",
            required = true,
            content = @Content(examples = @ExampleObject(value = """
                {
                  "name": "Java Web 开发",
                  "description": "面向对象与 Web 开发基础课程",
                  "schedule": "周三 3-4 节",
                  "credits": 3,
                  "maxStudents": 60,
                  "category": "专业课"
                }
                """))
        )
        @RequestBody Course course,
        @Parameter(hidden = true) Authentication auth) {
        return ApiResponse.ok("课程创建成功",
            teacherService.createCourse(course, getCurrentUser(auth)));
    }

    @Operation(summary = "更新课程", description = "按课程 ID 更新课程基本信息。")
    @PutMapping("/courses/{id}")
    public ApiResponse<?> updateCourse(@Parameter(description = "课程 ID") @PathVariable Long id,
                                       @RequestBody Course course,
                                       @Parameter(hidden = true) Authentication auth) {
        return ApiResponse.ok("课程更新成功", teacherService.updateCourse(id, course, getCurrentUser(auth)));
    }

    @Operation(summary = "删除课程", description = "删除指定课程。")
    @DeleteMapping("/courses/{id}")
    public ApiResponse<?> deleteCourse(@Parameter(description = "课程 ID") @PathVariable Long id) {
        teacherService.deleteCourse(id);
        return ApiResponse.ok("课程已删除");
    }

    @Operation(summary = "查看课程学生", description = "返回指定课程下的学生名单。")
    @GetMapping("/courses/{id}/students")
    public ApiResponse<?> courseStudents(@Parameter(description = "课程 ID") @PathVariable Long id) {
        return ApiResponse.ok(teacherService.getCourseStudents(id));
    }

    @Operation(summary = "发布作业", description = "为某门课程创建并发布新作业。")
    @PostMapping("/assignments")
    public ApiResponse<?> createAssignment(
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "作业信息",
            required = true,
            content = @Content(examples = @ExampleObject(value = """
                {
                  "title": "第一次实验报告",
                  "description": "完成课程实验并提交报告",
                  "course": { "id": 1 },
                  "dueDate": "2026-07-10T23:59:00",
                  "totalPoints": 100
                }
                """))
        )
        @RequestBody Assignment assignment,
        @Parameter(hidden = true) Authentication auth) {
        return ApiResponse.ok("作业发布成功",
            teacherService.createAssignment(assignment, getCurrentUser(auth)));
    }

    @Operation(summary = "查看课程作业", description = "返回指定课程下的作业列表。")
    @GetMapping("/courses/{id}/assignments")
    public ApiResponse<?> courseAssignments(@Parameter(description = "课程 ID") @PathVariable Long id) {
        return ApiResponse.ok(teacherService.getCourseAssignments(id));
    }

    @Operation(summary = "配置作业互评", description = "为指定作业开启或更新匿名互评配置。")
    @PostMapping("/assignments/{id}/peer-review")
    public ApiResponse<?> configurePeerReview(
        @Parameter(description = "作业 ID") @PathVariable Long id,
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "互评开关、时间窗、要求次数与加分规则",
            required = true,
            content = @Content(examples = @ExampleObject(value = """
                {
                  "peerReviewEnabled": true,
                  "peerReviewOpenAt": "2026-07-11 08:00:00",
                  "peerReviewCloseAt": "2026-07-13 23:59:59",
                  "peerReviewRequiredCount": 2,
                  "peerReviewBonusPerReview": 1.0,
                  "peerReviewBonusCap": 3.0,
                  "peerReviewPrompt": "请从逻辑结构、完整性、表达清晰度进行评价。"
                }
                """))
        )
        @RequestBody Map<String, Object> body,
        @Parameter(hidden = true) Authentication auth) {
        try {
            return ApiResponse.ok("互评配置已保存",
                teacherService.configurePeerReview(id, body, getCurrentUser(auth)));
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    @Operation(summary = "查看互评概览", description = "查看指定作业的互评配置和互评完成情况。")
    @GetMapping("/assignments/{id}/peer-review")
    public ApiResponse<?> peerReviewOverview(@Parameter(description = "作业 ID") @PathVariable Long id) {
        return ApiResponse.ok(teacherService.getPeerReviewOverview(id));
    }

    @Operation(summary = "查看作业提交记录", description = "查看指定作业下的学生提交记录。")
    @GetMapping("/assignments/{id}/submissions")
    public ApiResponse<?> pendingSubmissions(@Parameter(description = "作业 ID") @PathVariable Long id) {
        return ApiResponse.ok(teacherService.getPendingSubmissions(id));
    }

    @Operation(summary = "批改作业", description = "按提交记录 ID 为学生作业录入分数和评语。")
    @PostMapping("/submissions/{id}/grade")
    public ApiResponse<?> gradeSubmission(@Parameter(description = "提交记录 ID") @PathVariable Long id,
                                          @RequestBody GradeSubmissionRequest request,
                                          @Parameter(hidden = true) Authentication auth) {
        return ApiResponse.ok("评分成功",
            teacherService.gradeSubmission(id, request, getCurrentUser(auth)));
    }

    @Operation(summary = "查看作业成绩分析", description = "查看指定作业的成绩分布、及格率、均分等统计结果。")
    @GetMapping("/assignments/{id}/analysis")
    public ApiResponse<?> assignmentAnalysis(@Parameter(description = "作业 ID") @PathVariable Long id) {
        return ApiResponse.ok(teacherService.getAssignmentAnalysis(id));
    }

    @Operation(summary = "导出课程成绩", description = "将指定课程的成绩导出为 CSV 文件。")
    @io.swagger.v3.oas.annotations.responses.ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "导出成功",
            content = @Content(mediaType = "text/csv"))
    })
    @GetMapping("/courses/{id}/grades/export")
    public ResponseEntity<byte[]> exportCourseGrades(@Parameter(description = "课程 ID") @PathVariable Long id) {
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
