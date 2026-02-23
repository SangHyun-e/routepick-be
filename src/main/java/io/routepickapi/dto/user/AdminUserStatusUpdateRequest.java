package io.routepickapi.dto.user;

import io.routepickapi.entity.user.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record AdminUserStatusUpdateRequest(
    @NotNull
    @Schema(description = "변경할 사용자 상태", example = "BLOCKED")
    UserStatus status,
    @Schema(description = "변경 사유", example = "운영 정책 위반")
    String reason
) {
}
