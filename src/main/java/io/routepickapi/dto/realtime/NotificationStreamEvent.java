package io.routepickapi.dto.realtime;

import java.time.LocalDateTime;

public record NotificationStreamEvent(
    Long notificationId,
    String title,
    String message,
    long unreadCount,
    LocalDateTime createdAt
) {
}
