package io.routepickapi.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record KakaoLoginRequest(
    @NotBlank
    @Schema(description = "Kakao OAuth 인가 코드")
    String code
) {
}
