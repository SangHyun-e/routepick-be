package io.routepickapi.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "비밀번호 재설정 코드 요청")
public record PasswordResetRequest(
    @NotBlank @Email String email
) {

}
