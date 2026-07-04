package com.sms.controller;

import com.sms.dto.*;
import com.sms.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "登录、注册和密码找回接口")
@SecurityRequirements
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "用户登录", description = "根据用户名、密码和角色登录系统，返回 JWT、角色和跳转地址。")
    @io.swagger.v3.oas.annotations.responses.ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "登录成功"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "用户名、密码或角色不匹配")
    })
    @PostMapping("/login")
    public ResponseEntity<?> login(
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "登录用户名、密码与角色",
            required = true,
            content = @Content(examples = @ExampleObject(value = """
                {
                  "username": "student1",
                  "password": "student123",
                  "role": "STUDENT"
                }
                """))
        )
        @Valid @RequestBody LoginRequest req) {
        try {
            LoginResponse resp = authService.login(req);
            return ResponseEntity.ok(ApiResponse.ok("登录成功", resp));
        } catch (Exception e) {
            return ResponseEntity.status(401).body(ApiResponse.error("登录失败: " + e.getMessage()));
        }
    }

    @Operation(summary = "注册用户", description = "创建新用户账号，通常用于管理员或初始化阶段创建用户。")
    @io.swagger.v3.oas.annotations.responses.ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "注册成功"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "请求参数非法或用户已存在")
    })
    @PostMapping("/register")
    public ResponseEntity<?> register(
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "新用户注册信息",
            required = true,
            content = @Content(examples = @ExampleObject(value = """
                {
                  "username": "teacher2",
                  "password": "teacher123",
                  "displayName": "王老师",
                  "email": "teacher2@sms.com",
                  "role": "TEACHER"
                }
                """))
        )
        @Valid @RequestBody UserCreateRequest req) {
        try {
            var user = authService.register(req);
            return ResponseEntity.ok(ApiResponse.ok("注册成功", user));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @Operation(summary = "找回密码", description = "根据账号信息重置密码，成功后返回提示信息。")
    @io.swagger.v3.oas.annotations.responses.ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "密码重置成功"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "账号信息不匹配")
    })
    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "找回密码请求信息",
            required = true,
            content = @Content(examples = @ExampleObject(value = """
                {
                  "username": "student1",
                  "email": "student1@sms.com",
                  "newPassword": "newPassword123"
                }
                """))
        )
        @Valid @RequestBody ForgotPasswordRequest req) {
        try {
            authService.forgotPassword(req);
            return ResponseEntity.ok(ApiResponse.ok("密码重置成功，请使用新密码登录"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }
}
