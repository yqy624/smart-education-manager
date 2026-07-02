package com.sms.dto;

import lombok.Data;

@Data
public class GradeSubmissionRequest {
    private Double score;
    private String comment;
    private Long quickCommentId;
}
