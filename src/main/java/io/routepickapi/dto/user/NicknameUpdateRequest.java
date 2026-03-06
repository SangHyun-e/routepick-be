package io.routepickapi.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record NicknameUpdateRequest(
    @NotBlank @Size(max = 40)
    @Schema(description = "변경할 닉네임")
    String nickname
) {
}
