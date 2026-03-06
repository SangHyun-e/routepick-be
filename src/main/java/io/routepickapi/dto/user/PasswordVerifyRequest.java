package io.routepickapi.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "비밀번호 확인 요청")
public record PasswordVerifyRequest(
    @NotBlank @Schema(type = "string", format = "password") String password
) {

}
