package com.sms.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ProfileResponse {
    private String username;
    private String displayName;
    private String email;
    private String role;
}
