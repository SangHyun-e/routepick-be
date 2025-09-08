package io.routepickapi.security;

/**
 * SecurityContext 에 넣어둘 최소 정보
 */
public record AuthUser(Long id, String email, String nickname) {

}
