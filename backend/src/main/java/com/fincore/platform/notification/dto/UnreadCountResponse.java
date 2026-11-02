package com.fincore.platform.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Count of unread notifications for the user")
public record UnreadCountResponse(
        @Schema(description = "Number of unread notifications", example = "3")
        long unreadCount
) {}

