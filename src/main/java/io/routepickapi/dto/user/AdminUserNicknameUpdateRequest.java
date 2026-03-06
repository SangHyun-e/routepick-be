package io.routepickapi.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminUserNicknameUpdateRequest(
    @NotBlank @Size(max = 40) String nickname,
    @NotBlank @Size(max = 255) String reason
) {
}
