package io.routepickapi.dto.user;

import io.routepickapi.entity.user.User;
import io.routepickapi.entity.user.UserAuthProvider;
import io.routepickapi.entity.user.UserRole;
import io.routepickapi.entity.user.UserStatus;
import java.time.LocalDateTime;

public record AdminUserDetailResponse(
    Long id,
    String email,
    String nickname,
    UserRole role,
    UserStatus status,
    UserAuthProvider authProvider,
    boolean profileComplete,
    String withdrawReason,
    LocalDateTime rejoinRestrictedUntil,
    LocalDateTime rejoinRestrictionReleasedAt,
    Long rejoinRestrictionReleasedBy,
    String rejoinRestrictionReleaseReason,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    public static AdminUserDetailResponse from(User user) {
        return new AdminUserDetailResponse(
            user.getId(),
            user.getEmail(),
            user.getNickname(),
            user.getRole(),
            user.getStatus(),
            user.getAuthProvider(),
            user.isProfileComplete(),
            user.getWithdrawReason(),
            user.getRejoinRestrictedUntil(),
            user.getRejoinRestrictionReleasedAt(),
            user.getRejoinRestrictionReleasedBy(),
            user.getRejoinRestrictionReleaseReason(),
            user.getCreatedAt(),
            user.getUpdatedAt()
        );
    }
}
