package com.sms.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ProfileUpdateRequest {
    @Size(max = 50)
    private String displayName;

    @Email
    @Size(max = 100)
    private String email;

    @Size(min = 6, max = 100)
    private String newPassword;
}
