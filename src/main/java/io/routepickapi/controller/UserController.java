package io.routepickapi.controller;

import io.routepickapi.common.error.CustomException;
import io.routepickapi.common.error.ErrorType;
import io.routepickapi.dto.user.MeResponse;
import io.routepickapi.security.AuthUser;
import io.routepickapi.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    /**
     * 내 정보 조회
     * - SecurityConfig 에서 인증 필요로 보호 JwtAuthenticationFilter 가 SecurityContext 에 AuthUser 주입
     */
    @Operation(
        summary = "내 정보 조회",
        description = "현재 로그인한 사용자의 프로필 반환",
        security = {@SecurityRequirement(name = "bearerAuth")} // OpenAPI: Bearer 필요
    )
    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal AuthUser currentUser) {
        if (currentUser == null) {
            throw new CustomException(ErrorType.COMMON_UNAUTHORIZED);
        }
        log.info("GET /users/me - userId={}", currentUser.id());
        return userService.getMe(currentUser.id());
    }
}
