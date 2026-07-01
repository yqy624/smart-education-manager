package com.sms.service;

import com.sms.model.*;
import com.sms.repository.*;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TeacherServiceTest {

    @Test
    void analysisCalculatesPassRate() {
        CourseRepository courseRepository = mock(CourseRepository.class);
        AssignmentRepository assignmentRepository = mock(AssignmentRepository.class);
        SubmissionRepository submissionRepository = mock(SubmissionRepository.class);
        EnrollmentRepository enrollmentRepository = mock(EnrollmentRepository.class);
        NotificationService notificationService = mock(NotificationService.class);

        TeacherService service = new TeacherService(courseRepository, assignmentRepository, submissionRepository, enrollmentRepository, notificationService);
        Assignment assignment = Assignment.builder().id(1L).title("Quiz").build();
        when(assignmentRepository.findById(1L)).thenReturn(Optional.of(assignment));
        when(submissionRepository.findByAssignment(assignment)).thenReturn(List.of(
            Submission.builder().status(AssignmentStatus.GRADED).score(90d).build(),
            Submission.builder().status(AssignmentStatus.GRADED).score(60d).build(),
            Submission.builder().status(AssignmentStatus.GRADED).score(30d).build()
        ));

        var result = service.getAssignmentAnalysis(1L);
        assertEquals(3, result.get("total"));
        assertEquals(66.7, (Double) result.get("passRate"));
    }
}
