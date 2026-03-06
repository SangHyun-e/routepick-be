package io.routepickapi.dto.notification;

import io.routepickapi.entity.notification.Notification;
import io.routepickapi.entity.notification.NotificationResourceType;
import io.routepickapi.entity.notification.NotificationType;
import java.time.LocalDateTime;

public record NotificationResponse(
    Long id,
    NotificationType type,
    String title,
    String message,
    boolean read,
    LocalDateTime readAt,
    LocalDateTime createdAt,
    NotificationResourceType resourceType,
    Long resourceId,
    Long actorId,
    String actorNickname,
    String reason
) {
    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
            notification.getId(),
            notification.getType(),
            notification.getTitle(),
            notification.getMessage(),
            notification.isRead(),
            notification.getReadAt(),
            notification.getCreatedAt(),
            notification.getResourceType(),
            notification.getResourceId(),
            notification.getActorId(),
            notification.getActorNickname(),
            notification.getReason()
        );
    }
}
