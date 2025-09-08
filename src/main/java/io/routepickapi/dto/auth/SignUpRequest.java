package io.routepickapi.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "회원가입 요청")
public record SignUpRequest(
    @NotBlank @Email @Size(max = 255) String email,
    @NotBlank @Size(min = 8, max = 100)
    @Pattern(regexp = "^\\S+$", message = "password는 공백을 포함할 수 없습니다.")
    String password,
    @NotBlank @Size(max = 40) String nickname
) {

}
