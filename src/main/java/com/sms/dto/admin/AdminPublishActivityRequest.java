package com.sms.dto.admin;

import lombok.Data;

@Data
public class AdminPublishActivityRequest {
    private String title;
    private String content;
    private String audience;
    private String link;
}
