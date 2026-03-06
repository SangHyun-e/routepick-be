package io.routepickapi.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * 로그인 요청 DTO
 * - email / password
 */

public record LoginRequest(
    @Email @NotBlank String email,
    @NotBlank @Schema(type = "string", format = "password")
    String password
) {

}
