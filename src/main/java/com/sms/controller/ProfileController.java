package com.sms.controller;

import com.sms.dto.ApiResponse;
import com.sms.dto.ProfileUpdateRequest;
import com.sms.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
@Tag(name = "Profile", description = "当前登录用户的个人资料接口")
@SecurityRequirement(name = "bearerAuth")
public class ProfileController {

    private final AuthService authService;

    @Operation(summary = "查看个人资料", description = "返回当前登录用户的个人资料信息。")
    @GetMapping
    public ResponseEntity<?> getProfile(@Parameter(hidden = true) Authentication authentication) {
        try {
            return ResponseEntity.ok(ApiResponse.ok(authService.getProfile(authentication.getName())));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @Operation(summary = "更新个人资料", description = "更新当前登录用户的昵称、邮箱等个人资料信息。")
    @PutMapping
    public ResponseEntity<?> updateProfile(@Parameter(hidden = true) Authentication authentication,
                                           @Valid @RequestBody ProfileUpdateRequest req) {
        try {
            return ResponseEntity.ok(ApiResponse.ok("个人资料已更新", authService.updateProfile(authentication.getName(), req)));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }
}
