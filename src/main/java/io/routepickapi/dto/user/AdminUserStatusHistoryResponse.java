package io.routepickapi.dto.user;

import io.routepickapi.entity.user.UserStatusHistory;
import io.routepickapi.entity.user.UserStatus;
import java.time.LocalDateTime;

public record AdminUserStatusHistoryResponse(
    Long id,
    Long userId,
    UserStatus fromStatus,
    UserStatus toStatus,
    String reason,
    Long adminUserId,
    LocalDateTime createdAt
) {
    public static AdminUserStatusHistoryResponse from(UserStatusHistory history) {
        return new AdminUserStatusHistoryResponse(
            history.getId(),
            history.getUserId(),
            history.getFromStatus(),
            history.getToStatus(),
            history.getReason(),
            history.getAdminUserId(),
            history.getCreatedAt()
        );
    }
}
