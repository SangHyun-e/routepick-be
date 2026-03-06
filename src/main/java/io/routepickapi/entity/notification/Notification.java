package io.routepickapi.entity.notification;

import io.routepickapi.common.model.BaseTimeEntity;
import io.routepickapi.entity.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
    name = "notifications",
    indexes = {
        @Index(name = "idx_notifications_user_created", columnList = "user_id, created_at"),
        @Index(name = "idx_notifications_user_read", columnList = "user_id, is_read, created_at")
    }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "actor_id")
    private Long actorId;

    @Column(name = "actor_nickname", length = 40)
    private String actorNickname;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private NotificationType type;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, length = 255)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", length = 40)
    private NotificationResourceType resourceType;

    @Column(name = "resource_id")
    private Long resourceId;

    @Column(length = 255)
    private String reason;

    @Column(name = "is_read", nullable = false)
    private boolean read = false;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    public Notification(
        User user,
        NotificationType type,
        String title,
        String message,
        NotificationResourceType resourceType,
        Long resourceId,
        Long actorId,
        String actorNickname,
        String reason
    ) {
        if (user == null) {
            throw new IllegalArgumentException("user required");
        }
        if (type == null) {
            throw new IllegalArgumentException("type required");
        }
        if (title == null || title.isBlank() || title.length() > 120) {
            throw new IllegalArgumentException("invalid title");
        }
        if (message == null || message.isBlank() || message.length() > 255) {
            throw new IllegalArgumentException("invalid message");
        }
        this.user = user;
        this.type = type;
        this.title = title;
        this.message = message;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.actorId = actorId;
        this.actorNickname = actorNickname;
        this.reason = normalizeReason(reason);
    }

    public void markRead(LocalDateTime readAt) {
        if (this.read) {
            return;
        }
        this.read = true;
        this.readAt = readAt != null ? readAt : LocalDateTime.now();
    }

    private String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return null;
        }
        String trimmed = reason.trim();
        if (trimmed.length() > 255) {
            return trimmed.substring(0, 255);
        }
        return trimmed;
    }
}
