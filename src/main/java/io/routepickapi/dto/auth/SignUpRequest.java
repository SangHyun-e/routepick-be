package io.routepickapi.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import io.routepickapi.common.validation.ValidPassword;
import jakarta.validation.constraints.Size;

@Schema(description = "회원가입 요청")
public record SignUpRequest(
    @NotBlank @Email @Size(max = 255) String email,
    @ValidPassword @Schema(type = "string", format = "password")
    String password,
    @NotBlank @Size(max = 40) String nickname
) {

}
