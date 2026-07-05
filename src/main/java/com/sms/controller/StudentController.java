package com.sms.controller;

import com.sms.dto.ApiResponse;
import com.sms.model.User;
import com.sms.repository.UserRepository;
import com.sms.service.StudentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/student")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'TEACHER', 'STUDENT')")
@Tag(name = "Student", description = "学生侧课程、作业、互评与成绩接口")
@SecurityRequirement(name = "bearerAuth")
public class StudentController {

    private final StudentService studentService;
    private final UserRepository userRepository;

    private User getCurrentUser(Authentication auth) {
        return userRepository.findByUsername(auth.getName())
            .orElseThrow(() -> new RuntimeException("用户不存在"));
    }

    @Operation(summary = "查看学生首页数据", description = "返回学生首页的 KPI、成绩趋势、最近成绩和近期活动。")
    @GetMapping("/dashboard")
    public ApiResponse<?> dashboard(@Parameter(hidden = true) Authentication auth) {
        return ApiResponse.ok(studentService.getDashboard(getCurrentUser(auth)));
    }

    @Operation(summary = "查看全部可选课程", description = "返回当前可见、可供学生选修的课程列表。")
    @GetMapping("/courses")
    public ApiResponse<?> allCourses() {
        return ApiResponse.ok(studentService.getAllCourses());
    }

    @Operation(summary = "查看我的课程", description = "返回当前登录学生已选课程列表。")
    @GetMapping("/my-courses")
    public ApiResponse<?> myCourses(@Parameter(hidden = true) Authentication auth) {
        return ApiResponse.ok(studentService.getMyCourses(getCurrentUser(auth)));
    }

    @Operation(summary = "提交选课", description = "按课程 ID 发起选课，系统会校验课程状态、容量与时间冲突。")
    @PostMapping("/enroll/{courseId}")
    public ApiResponse<?> enroll(@Parameter(description = "课程 ID") @PathVariable Long courseId,
                                 @Parameter(hidden = true) Authentication auth) {
        try {
            studentService.enroll(courseId, getCurrentUser(auth));
            return ApiResponse.ok("选课成功");
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    @Operation(summary = "退选课程", description = "按课程 ID 退选已选课程。")
    @PostMapping("/drop/{courseId}")
    public ApiResponse<?> drop(@Parameter(description = "课程 ID") @PathVariable Long courseId,
                               @Parameter(hidden = true) Authentication auth) {
        studentService.drop(courseId, getCurrentUser(auth));
        return ApiResponse.ok("退课成功");
    }

    @Operation(summary = "查看课程作业", description = "返回当前学生在指定课程下可见的作业列表。")
    @GetMapping("/courses/{id}/assignments")
    public ApiResponse<?> assignments(@Parameter(description = "课程 ID") @PathVariable Long id,
                                      @Parameter(hidden = true) Authentication auth) {
        return ApiResponse.ok(studentService.getMyAssignments(id, getCurrentUser(auth)));
    }

    @Operation(summary = "提交作业", description = "提交指定作业的文本内容，并可携带已上传附件路径。")
    @PostMapping("/assignments/{id}/submit")
    public ApiResponse<?> submit(
        @Parameter(description = "作业 ID") @PathVariable Long id,
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "作业文本内容与附件路径",
            required = true,
            content = @Content(examples = @ExampleObject(value = """
                {
                  "content": "这是我的作业内容",
                  "filePath": "submissions/2026/07/report.pdf",
                  "fileName": "report.pdf"
                }
                """))
        )
        @RequestBody Map<String, String> body,
        @Parameter(hidden = true) Authentication auth) {
        String content = body.get("content");
        String filePath = body.get("filePath");
        String fileName = body.get("fileName");
        String stored = null;
        if (filePath != null && !filePath.isBlank()) {
            stored = filePath + (fileName != null && !fileName.isBlank() ? "::" + fileName : "");
        }
        return ApiResponse.ok("提交成功",
            studentService.submitAssignment(id, getCurrentUser(auth), content, stored));
    }

    @Operation(summary = "查看我的互评任务", description = "返回当前学生待完成和已完成的匿名互评任务分组。")
    @GetMapping("/peer-reviews")
    public ApiResponse<?> peerReviews(@Parameter(hidden = true) Authentication auth) {
        return ApiResponse.ok(studentService.getMyPeerReviews(getCurrentUser(auth)));
    }

    @Operation(summary = "提交匿名互评", description = "对指定作业的目标提交进行评分和评论，系统将校验是否已分配该互评任务。")
    @PostMapping("/peer-reviews/{assignmentId}/{targetSubmissionId}")
    public ApiResponse<?> submitPeerReview(
        @Parameter(description = "作业 ID") @PathVariable Long assignmentId,
        @Parameter(description = "被评价的提交记录 ID") @PathVariable Long targetSubmissionId,
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "互评评分与评论",
            required = true,
            content = @Content(examples = @ExampleObject(value = """
                {
                  "rating": 5,
                  "comment": "结构清晰，论证完整。"
                }
                """))
        )
        @RequestBody Map<String, Object> body,
        @Parameter(hidden = true) Authentication auth) {
        Integer rating = body.get("rating") == null ? null : Integer.parseInt(String.valueOf(body.get("rating")));
        String comment = String.valueOf(body.getOrDefault("comment", ""));
        try {
            return ApiResponse.ok("互评提交成功",
                studentService.submitPeerReview(getCurrentUser(auth), assignmentId, targetSubmissionId, rating, comment));
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    @Operation(summary = "查看我的成绩", description = "返回当前学生所有作业和课程的成绩明细。")
    @GetMapping("/grades")
    public ApiResponse<?> grades(@Parameter(hidden = true) Authentication auth) {
        return ApiResponse.ok(studentService.getMyGrades(getCurrentUser(auth)));
    }

    @Operation(summary = "查看课程平均分", description = "返回指定课程当前可计算出的平均成绩。")
    @GetMapping("/courses/{id}/average")
    public ApiResponse<?> courseAverage(@Parameter(description = "课程 ID") @PathVariable Long id) {
        Double avg = studentService.getCourseAverageScore(id);
        if (avg < 0) return ApiResponse.error("暂无成绩数据");
        return ApiResponse.ok(Map.of("courseId", id, "average", avg));
    }
}
