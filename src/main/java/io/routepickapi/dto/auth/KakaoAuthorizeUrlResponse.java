package io.routepickapi.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;

public record KakaoAuthorizeUrlResponse(
    @Schema(description = "Kakao OAuth 인증 URL")
    String authorizeUrl
) {
}
