package io.routepickapi.dto.user;

import io.routepickapi.entity.user.User;
import io.routepickapi.entity.user.UserAuthProvider;
import io.routepickapi.entity.user.UserRole;
import io.routepickapi.entity.user.UserStatus;
import java.time.LocalDateTime;

/**
 * 로그인한 본인 정보 응답 DTO
 * - 불필요한 내부 필드 노출 X
 */
public record MeResponse(
    Long id,
    String email,
    String nickname,
    UserRole role,
    UserStatus status,
    UserAuthProvider authProvider,
    boolean profileComplete,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {

    // 엔티티 -> DTO 변환 (static factory method)
    public static MeResponse from(User u) {
        return new MeResponse(
            u.getId(),
            u.getEmail(),
            u.getNickname(),
            u.getRole(),
            u.getStatus(),
            u.getAuthProvider(),
            u.isProfileComplete(),
            u.getCreatedAt(),
            u.getUpdatedAt()
        );
    }
}
