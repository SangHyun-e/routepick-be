package io.routepickapi.repository;

import io.routepickapi.entity.notification.Notification;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByUserId(Long userId, Pageable pageable);

    Page<Notification> findByUserIdAndRead(Long userId, boolean read, Pageable pageable);

    long countByUserIdAndReadFalse(Long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update Notification n
           set n.read = true,
               n.readAt = :readAt
         where n.user.id = :userId
           and n.read = false
        """)
    int markAllRead(@Param("userId") Long userId, @Param("readAt") LocalDateTime readAt);
}
