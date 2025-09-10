package io.routepickapi.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * 로그인 요청 DTO
 * - email / password
 */

public record LoginRequest(
    @Email @NotBlank String email,
    @NotBlank String password
) {

}
