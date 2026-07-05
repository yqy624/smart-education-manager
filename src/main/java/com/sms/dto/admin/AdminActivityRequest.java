package com.sms.dto.admin;

import lombok.Data;

@Data
public class AdminActivityRequest {
    private String title;
    private String content;
    private String audience;
    private String link;
}
