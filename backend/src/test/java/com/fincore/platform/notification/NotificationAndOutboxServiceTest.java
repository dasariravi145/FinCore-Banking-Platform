package com.fincore.platform.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincore.platform.common.error.UnauthorizedAccountAccessException;
import com.fincore.platform.notification.dto.NotificationResponse;
import com.fincore.platform.notification.dto.UnreadCountResponse;
import com.fincore.platform.notification.entity.Notification;
import com.fincore.platform.notification.entity.NotificationType;
import com.fincore.platform.notification.entity.OutboxEvent;
import com.fincore.platform.notification.entity.OutboxStatus;
import com.fincore.platform.notification.repository.NotificationRepository;
import com.fincore.platform.notification.repository.OutboxEventRepository;
import com.fincore.platform.notification.service.NotificationService;
import com.fincore.platform.notification.service.OutboxEventService;
import com.fincore.platform.user.entity.User;
import com.fincore.platform.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationAndOutboxServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private UserRepository userRepository;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    private NotificationService notificationService;
    private OutboxEventService outboxEventService;

    private User user1;
    private User user2;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(notificationRepository, userRepository);
        outboxEventService = new OutboxEventService(outboxEventRepository, objectMapper);

        user1 = new User("user1@test.local", "hashed", "User", "One", "+919876543001");
        user1.setId(UUID.randomUUID());

        user2 = new User("user2@test.local", "hashed", "User", "Two", "+919876543002");
        user2.setId(UUID.randomUUID());
    }

    @Test
    @DisplayName("Create notification and retrieve inbox")
    void testCreateNotificationAndGetInbox() {
        Notification n = new Notification(user1, "Transfer Received", "You received ₹5,000", NotificationType.TRANSFER_RECEIVED);
        n.setId(UUID.randomUUID());

        when(notificationRepository.save(any(Notification.class))).thenReturn(n);
        when(notificationRepository.findByUser_IdOrderByCreatedAtDesc(eq(user1.getId()), any()))
                .thenReturn(new PageImpl<>(List.of(n)));

        Notification created = notificationService.createNotification(user1, "Transfer Received", "You received ₹5,000", NotificationType.TRANSFER_RECEIVED);
        assertThat(created).isNotNull();

        Page<NotificationResponse> inbox = notificationService.getNotificationsForUser(user1.getId(), PageRequest.of(0, 10), false);
        assertThat(inbox.getContent()).hasSize(1);
        assertThat(inbox.getContent().get(0).title()).isEqualTo("Transfer Received");
    }

    @Test
    @DisplayName("Unread count returns accurate number of unread notifications")
    void testUnreadCount() {
        when(notificationRepository.countByUser_IdAndReadFalse(user1.getId())).thenReturn(4L);

        UnreadCountResponse response = notificationService.getUnreadCount(user1.getId());
        assertThat(response.unreadCount()).isEqualTo(4L);
    }

    @Test
    @DisplayName("Mark notification as read updates read status and timestamp")
    void testMarkAsRead_Success() {
        Notification n = new Notification(user1, "Loan Approved", "Your loan has been approved", NotificationType.LOAN_APPROVED);
        n.setId(UUID.randomUUID());

        when(notificationRepository.findById(n.getId())).thenReturn(Optional.of(n));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(i -> i.getArgument(0));

        NotificationResponse response = notificationService.markAsRead(n.getId(), user1.getId());

        assertThat(response.read()).isTrue();
        assertThat(response.readAt()).isNotNull();
    }

    @Test
    @DisplayName("Mark notification as read throws exception if notification belongs to another user")
    void testMarkAsRead_Unauthorized() {
        Notification n = new Notification(user1, "Security Alert", "New login from unknown IP", NotificationType.SECURITY_ALERT);
        n.setId(UUID.randomUUID());

        when(notificationRepository.findById(n.getId())).thenReturn(Optional.of(n));

        assertThatThrownBy(() -> notificationService.markAsRead(n.getId(), user2.getId()))
                .isInstanceOf(UnauthorizedAccountAccessException.class)
                .hasMessageContaining("User is not authorized to modify notification");
    }

    @Test
    @DisplayName("Publish event stores PENDING outbox event in database")
    void testPublishOutboxEvent() {
        when(outboxEventRepository.save(any(OutboxEvent.class))).thenAnswer(i -> {
            OutboxEvent e = i.getArgument(0);
            e.setId(UUID.randomUUID());
            return e;
        });

        OutboxEvent event = outboxEventService.publishEvent("LOAN", "LN-001", "LOAN_APPLIED", Map.of("amount", 50000));

        assertThat(event).isNotNull();
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(event.getAggregateType()).isEqualTo("LOAN");
        assertThat(event.getPayload()).contains("50000");
    }

    @Test
    @DisplayName("Process pending outbox events updates status to PROCESSED")
    void testProcessPendingOutboxEvents() {
        OutboxEvent e1 = new OutboxEvent("TRANSFER", "TX-001", "TRANSFER_COMPLETED", "{\"amount\":1000}");
        e1.setId(UUID.randomUUID());

        when(outboxEventRepository.findByStatusOrderByCreatedAtAsc(eq(OutboxStatus.PENDING), any()))
                .thenReturn(List.of(e1));
        when(outboxEventRepository.save(any(OutboxEvent.class))).thenAnswer(i -> i.getArgument(0));

        int processed = outboxEventService.processPendingEvents(10);

        assertThat(processed).isEqualTo(1);
        assertThat(e1.getStatus()).isEqualTo(OutboxStatus.PROCESSED);
        assertThat(e1.getProcessedAt()).isNotNull();
    }
}

