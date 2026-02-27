package io.routepickapi.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

public record WithdrawRequest(
    @Size(max = 255)
    @Schema(description = "탈퇴 사유", example = "서비스를 더 이상 사용하지 않음")
    String reason
) {
}
