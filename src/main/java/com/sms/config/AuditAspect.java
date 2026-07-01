package com.sms.config;

import com.sms.model.AuditLog;
import com.sms.repository.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Aspect
@Component
@RequiredArgsConstructor
public class AuditAspect {

    private final AuditLogRepository auditLogRepository;

    @AfterReturning("@annotation(org.springframework.web.bind.annotation.PostMapping) " +
                    "|| @annotation(org.springframework.web.bind.annotation.PutMapping) " +
                    "|| @annotation(org.springframework.web.bind.annotation.DeleteMapping)")
    public void logChangeOperations(JoinPoint jp) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated()) return;

            HttpServletRequest req = ((ServletRequestAttributes)
                RequestContextHolder.currentRequestAttributes()).getRequest();

            AuditLog log = AuditLog.builder()
                .username(auth.getName())
                .role(auth.getAuthorities().stream().findFirst()
                    .map(a -> a.getAuthority().replace("ROLE_", "")).orElse("UNKNOWN"))
                .action(req.getMethod() + " " + req.getRequestURI())
                .details(jp.getSignature().toShortString())
                .ipAddress(req.getRemoteAddr())
                .build();
            auditLogRepository.save(log);
        } catch (Exception ignored) {}
    }
}
