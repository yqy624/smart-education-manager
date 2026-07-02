package com.sms.repository;

import com.sms.model.TeacherCommentUsageHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TeacherCommentUsageHistoryRepository extends JpaRepository<TeacherCommentUsageHistory, Long> {
}
