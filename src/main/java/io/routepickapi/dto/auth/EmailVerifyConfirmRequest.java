package io.routepickapi.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record EmailVerifyConfirmRequest(
    @NotBlank @Email String email,

    @NotBlank
    @Pattern(regexp = "^[0-9]{6}$", message = "code는 6자리 숫자여야 합니다.")
    String code
) {

}
