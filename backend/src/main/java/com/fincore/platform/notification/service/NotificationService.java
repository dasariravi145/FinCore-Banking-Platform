package com.fincore.platform.notification.service;

import com.fincore.platform.common.error.ResourceNotFoundException;
import com.fincore.platform.common.error.UnauthorizedAccountAccessException;
import com.fincore.platform.notification.dto.NotificationResponse;
import com.fincore.platform.notification.dto.UnreadCountResponse;
import com.fincore.platform.notification.entity.Notification;
import com.fincore.platform.notification.entity.NotificationType;
import com.fincore.platform.notification.repository.NotificationRepository;
import com.fincore.platform.user.entity.User;
import com.fincore.platform.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public NotificationService(NotificationRepository notificationRepository,
                               UserRepository userRepository) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public Notification createNotification(User user, String title, String message, NotificationType type) {
        Notification notification = new Notification(user, title, message, type);
        Notification saved = notificationRepository.save(notification);
        log.info("Created notification [{}] of type [{}] for user [{}]", saved.getId(), type, user.getEmail());
        return saved;
    }

    @Transactional(readOnly = true)
    public Page<NotificationResponse> getNotificationsForUser(UUID userId, Pageable pageable, Boolean unreadOnly) {
        Page<Notification> page;
        if (Boolean.TRUE.equals(unreadOnly)) {
            page = notificationRepository.findByUser_IdAndReadOrderByCreatedAtDesc(userId, false, pageable);
        } else {
            page = notificationRepository.findByUser_IdOrderByCreatedAtDesc(userId, pageable);
        }
        return page.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public UnreadCountResponse getUnreadCount(UUID userId) {
        long count = notificationRepository.countByUser_IdAndReadFalse(userId);
        return new UnreadCountResponse(count);
    }

    @Transactional
    public NotificationResponse markAsRead(UUID notificationId, UUID userId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found: " + notificationId));

        if (!notification.getUser().getId().equals(userId)) {
            throw new UnauthorizedAccountAccessException("User is not authorized to modify notification: " + notificationId);
        }

        notification.markAsRead();
        Notification saved = notificationRepository.save(notification);
        return toResponse(saved);
    }

    private NotificationResponse toResponse(Notification n) {
        return new NotificationResponse(
                n.getId(),
                n.getTitle(),
                n.getMessage(),
                n.getType().name(),
                n.isRead(),
                n.getCreatedAt(),
                n.getReadAt()
        );
    }
}

