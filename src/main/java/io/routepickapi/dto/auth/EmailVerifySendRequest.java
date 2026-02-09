package io.routepickapi.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record EmailVerifySendRequest(
    @NotBlank
    @Email
    String email
) {

}

