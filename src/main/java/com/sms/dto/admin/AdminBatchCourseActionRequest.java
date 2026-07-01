package com.sms.dto.admin;

import lombok.Data;

import java.util.List;

@Data
public class AdminBatchCourseActionRequest {
    private List<Long> courseIds;
}
