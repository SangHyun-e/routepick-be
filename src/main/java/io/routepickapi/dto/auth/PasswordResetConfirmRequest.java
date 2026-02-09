package io.routepickapi.dto.auth;

import io.routepickapi.common.validation.ValidPassword;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Schema(description = "비밀번호 재설정 코드 확인")
public record PasswordResetConfirmRequest(
    @NotBlank @Email String email,
    @NotBlank
    @Pattern(regexp = "^[0-9]{6}$", message = "code는 6자리 숫자여야 합니다.")
    String code,
    @ValidPassword @Schema(type = "string", format = "password")
    String newPassword
) {

}
