package com.sms.repository;

import com.sms.model.Assignment;
import com.sms.model.Course;
import com.sms.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AssignmentRepository extends JpaRepository<Assignment, Long> {
    List<Assignment> findByCourse(Course course);
    List<Assignment> findByTeacher(User teacher);
}
