package com.sms.repository;

import com.sms.model.Course;
import com.sms.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface CourseRepository extends JpaRepository<Course, Long> {
    List<Course> findByTeacher(User teacher);
    List<Course> findByNameContaining(String keyword);
    List<Course> findByVisibleTrue();

    @Query("SELECT c FROM Course c WHERE (" +
           ":keyword IS NULL OR LOWER(c.name) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(c.description) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(c.teacher.displayName) LIKE LOWER(CONCAT('%', :keyword, '%')))" )
    Page<Course> search(@Param("keyword") String keyword, Pageable pageable);

    @Query("SELECT c FROM Course c WHERE c.visible = true AND (" +
           ":keyword IS NULL OR LOWER(c.name) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(c.description) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(c.teacher.displayName) LIKE LOWER(CONCAT('%', :keyword, '%')))" )
    Page<Course> searchVisible(@Param("keyword") String keyword, Pageable pageable);

    long countByCreatedAtAfter(LocalDateTime time);
}
