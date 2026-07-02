package com.sms.repository;

import com.sms.model.CommentCategory;
import com.sms.model.TeacherCommentMemory;
import com.sms.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TeacherCommentMemoryRepository extends JpaRepository<TeacherCommentMemory, Long> {
    Optional<TeacherCommentMemory> findByTeacherAndNormalizedText(User teacher, String normalizedText);

    Optional<TeacherCommentMemory> findByIdAndTeacher(Long id, User teacher);

    List<TeacherCommentMemory> findByTeacherOrderByUsageCountDescLastUsedAtDesc(User teacher);

    List<TeacherCommentMemory> findByTeacherAndCategoryOrderByUsageCountDescLastUsedAtDesc(User teacher, CommentCategory category);
}
