package io.routepickapi.service;

import io.routepickapi.common.error.CustomException;
import io.routepickapi.common.error.ErrorType;
import io.routepickapi.dto.notification.NotificationResponse;
import io.routepickapi.entity.notification.Notification;
import io.routepickapi.entity.notification.NotificationResourceType;
import io.routepickapi.entity.notification.NotificationType;
import io.routepickapi.entity.user.User;
import io.routepickapi.repository.NotificationRepository;
import io.routepickapi.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final RealtimeStreamService realtimeStreamService;

    @Transactional(readOnly = true)
    public Page<NotificationResponse> list(Long userId, Boolean read, Pageable pageable) {
        if (userId == null) {
            throw new CustomException(ErrorType.COMMON_UNAUTHORIZED);
        }
        Page<Notification> page = read == null
            ? notificationRepository.findByUserId(userId, pageable)
            : notificationRepository.findByUserIdAndRead(userId, read, pageable);
        return page.map(NotificationResponse::from);
    }

    public void markRead(Long userId, Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
            .orElseThrow(() -> new CustomException(ErrorType.COMMON_NOT_FOUND));

        if (!notification.getUser().getId().equals(userId)) {
            throw new CustomException(ErrorType.COMMON_FORBIDDEN);
        }

        notification.markRead(LocalDateTime.now());
    }

    public void delete(Long userId, Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
            .orElseThrow(() -> new CustomException(ErrorType.COMMON_NOT_FOUND));

        if (!notification.getUser().getId().equals(userId)) {
            throw new CustomException(ErrorType.COMMON_FORBIDDEN);
        }

        notificationRepository.delete(notification);
    }

    public int deleteAll(Long userId) {
        return notificationRepository.deleteByUserId(userId);
    }

    public int deleteRead(Long userId) {
        return notificationRepository.deleteByUserIdAndReadTrue(userId);
    }

    public int markAllRead(Long userId) {
        return notificationRepository.markAllRead(userId, LocalDateTime.now());
    }

    public void createNotification(
        User recipient,
        NotificationType type,
        String title,
        String message,
        NotificationResourceType resourceType,
        Long resourceId,
        Long actorId,
        String actorNickname,
        String reason
    ) {
        if (recipient == null) {
            return;
        }
        if (actorId != null && actorId.equals(recipient.getId())) {
            return;
        }
        Notification notification = new Notification(
            recipient,
            type,
            title,
            message,
            resourceType,
            resourceId,
            actorId,
            actorNickname,
            reason
        );
        notificationRepository.save(notification);
        realtimeStreamService.publishNotification(notification);
    }

    public void createNotificationByUserId(
        Long recipientUserId,
        NotificationType type,
        String title,
        String message,
        NotificationResourceType resourceType,
        Long resourceId,
        Long actorId,
        String actorNickname,
        String reason
    ) {
        if (recipientUserId == null) {
            return;
        }
        if (actorId != null && actorId.equals(recipientUserId)) {
            return;
        }
        User recipient = userRepository.findById(recipientUserId).orElse(null);
        if (recipient == null) {
            return;
        }
        createNotification(
            recipient,
            type,
            title,
            message,
            resourceType,
            resourceId,
            actorId,
            actorNickname,
            reason
        );
    }

    public void createBulkNotifications(
        List<User> recipients,
        NotificationType type,
        String title,
        String message,
        NotificationResourceType resourceType,
        Long resourceId
    ) {
        if (recipients == null || recipients.isEmpty()) {
            return;
        }
        for (User recipient : recipients) {
            createNotification(recipient, type, title, message, resourceType, resourceId, null,
                null, null);
        }
    }
}
