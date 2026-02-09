package io.routepickapi.security;

/**
 * SecurityContext 에 넣어둘 최소 정보
 */
import io.routepickapi.entity.user.UserRole;

public record AuthUser(Long id, String email, String nickname, UserRole role) {

}
