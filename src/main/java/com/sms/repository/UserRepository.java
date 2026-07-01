package com.sms.repository;

import com.sms.model.RoleType;
import com.sms.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    List<User> findByRole(com.sms.model.RoleType role);
    boolean existsByUsername(String username);

    @Query("SELECT COUNT(u) FROM User u WHERE u.role = ?1")
    long countByRole(com.sms.model.RoleType role);

    long countByCreatedAtAfter(LocalDateTime time);

    long countByLastLoginAfter(LocalDateTime time);

    @Query("SELECT u FROM User u WHERE " +
           "(:keyword IS NULL OR LOWER(u.username) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "  OR LOWER(u.displayName) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "  OR LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "AND (:role IS NULL OR u.role = :role) " +
           "AND (:enabled IS NULL OR u.enabled = :enabled)")
    Page<User> search(@Param("keyword") String keyword,
                      @Param("role") RoleType role,
                      @Param("enabled") Boolean enabled,
                      Pageable pageable);
}
