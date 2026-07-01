package com.sms.repository;

import com.sms.model.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    List<AuditLog> findByTimestampAfterOrderByTimestampDesc(LocalDateTime after);
    List<AuditLog> findAllByOrderByTimestampDesc();

    /**
     * 【模块2新增】审计日志关键字分页查询。
     * keyword 对用户名 / 操作 / 角色 / IP 做模糊匹配，为 null 时返回全部（按时间倒序）。
     */
    @Query("SELECT a FROM AuditLog a WHERE " +
           ":keyword IS NULL OR LOWER(a.username) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(a.action) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(a.role) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(a.ipAddress) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    Page<AuditLog> search(@Param("keyword") String keyword, Pageable pageable);
}
