package com.sms.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ForgotPasswordRequest {
    @NotBlank
    private String username;

    @NotBlank
    private String role;

    private String email;

    @NotBlank
    private String newPassword;
}
