package com.fincore.platform.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Customer in-app notification item")
public record NotificationResponse(
        UUID id,
        String title,
        String message,
        String type,
        boolean read,
        Instant createdAt,
        Instant readAt
) {}

