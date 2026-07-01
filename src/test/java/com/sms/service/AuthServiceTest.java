package com.sms.service;

import com.sms.dto.LoginRequest;
import com.sms.model.RoleType;
import com.sms.model.User;
import com.sms.repository.UserRepository;
import com.sms.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthServiceTest {

    @Test
    void rejectsRoleMismatch() {
        UserRepository userRepository = mock(UserRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        AuthenticationManager authenticationManager = mock(AuthenticationManager.class);
        JwtTokenProvider jwtTokenProvider = mock(JwtTokenProvider.class);

        AuthService service = new AuthService(userRepository, passwordEncoder, authenticationManager, jwtTokenProvider);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(User.builder().username("alice").role(RoleType.STUDENT).password("x").build()));

        LoginRequest req = new LoginRequest();
        req.setUsername("alice");
        req.setPassword("pw");
        req.setRole("TEACHER");

        assertThrows(RuntimeException.class, () -> service.login(req));
    }
}
