package com.sms.config;

import com.sms.model.RoleType;
import com.sms.model.User;
import com.sms.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@RequiredArgsConstructor
public class DataInitializer {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @PostConstruct
    public void init() {
        // Always recreate default users to ensure correct password encoding
        createIfMissing("admin", "admin123", "系统管理员", "admin@sms.com", RoleType.ADMIN);
        createIfMissing("teacher1", "teacher123", "张老师", "teacher1@sms.com", RoleType.TEACHER);
        createIfMissing("student1", "student123", "李同学", "student1@sms.com", RoleType.STUDENT);

        long count = userRepository.count();
        if (count > 0) {
            System.out.println("\u2705 默认用户已确保:");
            System.out.println("   admin / admin123  (管理员)");
            System.out.println("   teacher1 / teacher123  (教师)");
            System.out.println("   student1 / student123  (学生)");
        }
    }

    private void createIfMissing(String username, String password, String displayName, String email, RoleType role) {
        if (!userRepository.findByUsername(username).isPresent()) {
            userRepository.save(User.builder()
                .username(username)
                .password(passwordEncoder.encode(password))
                .displayName(displayName)
                .email(email)
                .role(role)
                .build());
            System.out.println("\u521b建用户: " + username);
        }
    }
}
