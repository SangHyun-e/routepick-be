package io.routepickapi.dto.user;

import io.routepickapi.common.validation.ValidPassword;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "비밀번호 변경 요청")
public record PasswordChangeRequest(
    @NotBlank @Schema(type = "string", format = "password", description = "현재 비밀번호")
    String currentPassword,
    @ValidPassword @Schema(type = "string", format = "password", description = "새 비밀번호")
    String newPassword,
    @NotBlank @Schema(type = "string", format = "password", description = "새 비밀번호 확인")
    String confirmPassword
) {
}
