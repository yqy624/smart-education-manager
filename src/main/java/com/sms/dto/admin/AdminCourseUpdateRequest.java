package com.sms.dto.admin;

import lombok.Data;

@Data
public class AdminCourseUpdateRequest {
    private String name;
    private Long teacherId;
    private String schedule;
    private Integer maxStudents;
    private String description;
    private String category;
    private Integer credits;
}
