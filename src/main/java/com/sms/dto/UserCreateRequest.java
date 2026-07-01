package com.sms.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserCreateRequest {
    @NotBlank @Size(min = 3, max = 50)
    private String username;
    @NotBlank @Size(min = 6, max = 100)
    private String password;
    @Size(max = 50)
    private String displayName;
    @Email @Size(max = 100)
    private String email;
    @NotBlank
    private String role; // ADMIN, TEACHER, STUDENT
}
