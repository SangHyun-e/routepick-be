package io.routepickapi.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "회원가입 결과")
public record SignUpResponse(
    Long id,
    String email,
    String nickname
) {

}
