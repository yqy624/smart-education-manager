package com.sms.controller;

import com.sms.dto.ApiResponse;
import com.sms.dto.ProfileUpdateRequest;
import com.sms.service.AuthService;
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
public class ProfileController {

    private final AuthService authService;

    @GetMapping
    public ResponseEntity<?> getProfile(Authentication authentication) {
        try {
            return ResponseEntity.ok(ApiResponse.ok(authService.getProfile(authentication.getName())));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PutMapping
    public ResponseEntity<?> updateProfile(Authentication authentication, @Valid @RequestBody ProfileUpdateRequest req) {
        try {
            return ResponseEntity.ok(ApiResponse.ok("个人资料已更新", authService.updateProfile(authentication.getName(), req)));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }
}
