package io.routepickapi.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

public record AdminUserRejoinRestrictionReleaseRequest(
    @Size(max = 255)
    @Schema(description = "재가입 제한 해제 사유", example = "오판으로 해제")
    String reason
) {
}
