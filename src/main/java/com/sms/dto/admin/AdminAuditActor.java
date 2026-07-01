package com.sms.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AdminAuditActor {
    private String username;
    private String role;
    private String ipAddress;
}
