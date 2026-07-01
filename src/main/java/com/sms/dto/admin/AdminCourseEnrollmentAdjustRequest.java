package com.sms.dto.admin;

import lombok.Data;

import java.util.List;

@Data
public class AdminCourseEnrollmentAdjustRequest {
    private List<Long> addStudentIds;
    private List<Long> removeStudentIds;
    private Integer maxStudents;
}
