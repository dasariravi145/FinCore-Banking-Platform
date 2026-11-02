package com.fincore.platform.notification.repository;

import com.fincore.platform.notification.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Page<Notification> findByUser_IdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Page<Notification> findByUser_IdAndReadOrderByCreatedAtDesc(UUID userId, boolean read, Pageable pageable);

    long countByUser_IdAndReadFalse(UUID userId);
}

