package com.sms.repository;

import com.sms.model.PublishedActivity;
import com.sms.model.PublishedActivityStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PublishedActivityRepository extends JpaRepository<PublishedActivity, Long> {
    List<PublishedActivity> findTop8ByOrderByUpdatedAtDesc();
    List<PublishedActivity> findTop5ByStatusOrderByPublishedAtDesc(PublishedActivityStatus status);
    List<PublishedActivity> findTop3ByStatusOrderByPublishedAtDesc(PublishedActivityStatus status);
}
