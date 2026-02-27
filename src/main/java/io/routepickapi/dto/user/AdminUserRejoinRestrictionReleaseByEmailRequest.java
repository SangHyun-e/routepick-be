package io.routepickapi.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminUserRejoinRestrictionReleaseByEmailRequest(
    @NotBlank
    @Email
    @Schema(description = "해제 대상 이메일", example = "user@example.com")
    String email,
    @Size(max = 255)
    @Schema(description = "재가입 제한 해제 사유", example = "요청 확인 후 해제")
    String reason
) {
}
