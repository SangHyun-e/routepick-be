package io.routepickapi.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;

public record KakaoLogoutUrlResponse(
    @Schema(description = "Kakao OAuth 로그아웃 URL")
    String logoutUrl
) {
}
