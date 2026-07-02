package com.sms.controller;

import com.sms.dto.ApiResponse;
import com.sms.model.CommentCategory;
import com.sms.model.RoleType;
import com.sms.model.User;
import com.sms.repository.UserRepository;
import com.sms.service.TeacherCommentMemoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/teacher/comment-memories")
@RequiredArgsConstructor
@PreAuthorize("hasRole('TEACHER')")
public class TeacherCommentController {

    private final TeacherCommentMemoryService teacherCommentMemoryService;
    private final UserRepository userRepository;

    @GetMapping
    public ApiResponse<?> myCommentMemories(@RequestParam(required = false) CommentCategory category,
                                            Authentication auth) {
        User teacher = userRepository.findByUsername(auth.getName())
            .orElseThrow(() -> new RuntimeException("用户不存在"));
        if (teacher.getRole() != RoleType.TEACHER) {
            throw new RuntimeException("仅教师可访问快捷评语库");
        }
        return ApiResponse.ok(teacherCommentMemoryService.listTeacherComments(teacher, category));
    }
}
