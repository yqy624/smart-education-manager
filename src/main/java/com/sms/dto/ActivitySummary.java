package com.sms.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ActivitySummary {
    private Long id;
    private String title;
    private String content;
    private String audience;
    private String link;
    private String status;
    private Object publishedAt;
    private Object createdAt;
}
