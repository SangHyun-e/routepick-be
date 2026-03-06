package io.routepickapi.dto.user;

import io.routepickapi.entity.user.User;
import io.routepickapi.entity.user.UserRole;
import io.routepickapi.entity.user.UserStatus;
import java.time.LocalDateTime;

public record AdminUserListItemResponse(
    Long id,
    String email,
    String nickname,
    UserRole role,
    UserStatus status,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    public static AdminUserListItemResponse from(User user) {
        return new AdminUserListItemResponse(
            user.getId(),
            user.getEmail(),
            user.getNickname(),
            user.getRole(),
            user.getStatus(),
            user.getCreatedAt(),
            user.getUpdatedAt()
        );
    }
}
