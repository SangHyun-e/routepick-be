package io.routepickapi.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record AdminUserRejoinRestrictionLockByEmailRequest(
    @NotBlank
    @Email
    @Schema(description = "대상 이메일", example = "user@example.com")
    String email
) {
}
