package com.sms.service;

import com.sms.dto.*;
import com.sms.model.*;
import com.sms.repository.*;
import com.sms.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;

    public LoginResponse login(LoginRequest req) {
        authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(req.getUsername(), req.getPassword()));

        User user = userRepository.findByUsername(req.getUsername())
            .orElseThrow(() -> new RuntimeException("用户不存在"));
        if (!user.getRole().name().equalsIgnoreCase(req.getRole())) {
            throw new RuntimeException("所选角色与账号不匹配");
        }
        user.setLastLogin(java.time.LocalDateTime.now());
        userRepository.save(user);

        String token = jwtTokenProvider.generateToken(user.getUsername(), user.getRole().name());
        LoginResponse resp = new LoginResponse();
        resp.setToken(token);
        resp.setUsername(user.getUsername());
        resp.setDisplayName(user.getDisplayName());
        resp.setRole(user.getRole().name());
        resp.setRedirectUrl(switch (user.getRole()) {
            case ADMIN -> "/admin/dashboard.html";
            case TEACHER -> "/teacher/dashboard.html";
            case STUDENT -> "/student/dashboard.html";
        });
        resp.setExpiresIn(86400000L);
        return resp;
    }

    @Transactional
    public User register(UserCreateRequest req) {
        if (userRepository.existsByUsername(req.getUsername())) {
            throw new RuntimeException("用户名已存在");
        }
        RoleType role;
        try {
            role = RoleType.valueOf(req.getRole().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("无效角色，可用: ADMIN, TEACHER, STUDENT");
        }
        User user = User.builder()
            .username(req.getUsername())
            .password(passwordEncoder.encode(req.getPassword()))
            .displayName(req.getDisplayName() != null ? req.getDisplayName() : req.getUsername())
            .email(req.getEmail())
            .role(role)
            .build();
        return userRepository.save(user);
    }

    @Transactional
    public void forgotPassword(ForgotPasswordRequest req) {
        User user = userRepository.findByUsername(req.getUsername())
            .orElseThrow(() -> new RuntimeException("用户不存在"));
        RoleType role;
        try {
            role = RoleType.valueOf(req.getRole().trim().toUpperCase());
        } catch (Exception e) {
            throw new RuntimeException("角色无效");
        }
        if (user.getRole() != role) {
            throw new RuntimeException("账号角色不匹配");
        }
        if (req.getEmail() != null && !req.getEmail().isBlank()) {
            String email = user.getEmail() == null ? "" : user.getEmail().trim();
            if (!email.equalsIgnoreCase(req.getEmail().trim())) {
                throw new RuntimeException("邮箱不匹配");
            }
        }
        user.setPassword(passwordEncoder.encode(req.getNewPassword()));
        userRepository.save(user);
    }

    public ProfileResponse getProfile(String username) {
        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new RuntimeException("用户不存在"));
        return toProfileResponse(user);
    }

    @Transactional
    public ProfileResponse updateProfile(String username, ProfileUpdateRequest req) {
        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new RuntimeException("用户不存在"));
        String displayName = req.getDisplayName() == null ? "" : req.getDisplayName().trim();
        if (displayName.isBlank()) {
            displayName = user.getUsername();
        }
        user.setDisplayName(displayName);
        user.setEmail(req.getEmail() == null ? null : req.getEmail().trim());
        if (req.getNewPassword() != null && !req.getNewPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(req.getNewPassword()));
        }
        return toProfileResponse(userRepository.save(user));
    }

    private ProfileResponse toProfileResponse(User user) {
        return new ProfileResponse(
            user.getUsername(),
            user.getDisplayName(),
            user.getEmail(),
            user.getRole().name()
        );
    }
}
