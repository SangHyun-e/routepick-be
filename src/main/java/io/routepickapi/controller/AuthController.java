package io.routepickapi.controller;

import io.routepickapi.common.error.CustomException;
import io.routepickapi.common.error.ErrorType;
import io.routepickapi.dto.IssuedTokens;
import io.routepickapi.dto.auth.LoginRequest;
import io.routepickapi.dto.auth.LoginResponse;
import io.routepickapi.dto.auth.SignUpRequest;
import io.routepickapi.dto.auth.SignUpResponse;
import io.routepickapi.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/auth")
@Validated
public class AuthController {

    private static final String REFRESH_COOKIE = "RP_REFRESH"; // 쿠키 이름 (고정)
    private final AuthService authService;

    @Operation(summary = "회원가입", description = "이메일/비밀번호/닉네임 회원가입")
    @PostMapping("/signup")
    public ResponseEntity<SignUpResponse> signUp(@Valid @RequestBody SignUpRequest req) {
        log.debug("POST /auth/signup - request received (email={}, nickname={})", req.email(),
            req.nickname());
        SignUpResponse res = authService.signUp(req);
        log.info("User signed up: id={}, email={}", res.id(), res.email());
        return ResponseEntity.created(URI.create("/users/" + res.id())).body(res);
    }

    // 공통 로직 통합 (refresh 쿠키 생성)
    private ResponseCookie buildRefreshCookie(String value, long maxAgeSec) {
        return ResponseCookie.from(REFRESH_COOKIE, value)
            .httpOnly(true) // JS 접근 차단
            .secure(false) // prod 는 true(HTTPS)로
            .sameSite("Lax") // prod 에서 cross-site 필요하면 "None"
            .path("/")
            .maxAge(Duration.ofSeconds(maxAgeSec))
            .build();
    }

    // 공통 로직 통합 (토큰응답 + 쿠키 헤더 세팅)
    private ResponseEntity<LoginResponse> okWithRefresh(IssuedTokens t) {
        ResponseCookie cookie = buildRefreshCookie(t.refreshToken(), t.refreshTtlSec());
        LoginResponse body = new LoginResponse(t.accessToken(), t.accessExpiresInSec());

        log.debug("Set refresh cookie (name={}, maxAgeSec={})", REFRESH_COOKIE, t.refreshTtlSec());
        log.debug("Respond with access token (expiresInSec={})", t.accessExpiresInSec());
        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, cookie.toString())
            .body(body);
    }

    @Operation(summary = "로그인", description = "이메일/비밀번호 로그인 -> JWT 발급")
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest req) {
        log.debug("POST /auth/login - request received (email={})", req.email());
        IssuedTokens tokens = authService.loginIssueTokens(req);
        log.info("Login Success (email={})", req.email());
        return okWithRefresh(tokens);
    }

    @Operation(summary = "토큰 재발급", description = "HttpOnly 쿠키의 refresh 토큰으로 access 토큰 재발급")
    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(
        @CookieValue(name = REFRESH_COOKIE, required = false) String refreshCookie
    ) {
        log.debug("POST /auth/refresh - request received (hasCookie={})", refreshCookie != null);
        if (refreshCookie == null || refreshCookie.isBlank()) {
            log.warn("Refresh failed: no refresh cookie");
            // refresh 쿠키 없음 -> 401
            throw new CustomException(ErrorType.AUTH_TOKEN_INVALID);
        }
        IssuedTokens tokens = authService.refresh(refreshCookie);
        log.info("Refresh success (new access expSec={}, new refresh ttlSec={})",
            tokens.accessExpiresInSec(), tokens.refreshTtlSec());
        return okWithRefresh(tokens);
    }

    @Operation(summary = "로그아웃", description = "access 블랙리스트 등록 + refresh 제거 + 쿠키 제거")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
        @CookieValue(name = REFRESH_COOKIE, required = false) String refreshCookie,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader
    ) {
        log.debug("POST /auth/logout - request received (hasAuthHeader={}, hasRefreshCookie={})",
            authHeader != null, refreshCookie != null);
        authService.logout(authHeader, refreshCookie);

        ResponseCookie clear = ResponseCookie.from(REFRESH_COOKIE, "")
            .httpOnly(true)
            .secure(false)
            .sameSite("Lax")
            .path("/")
            .maxAge(Duration.ZERO)
            .build();

        log.info("Logout success (Refresh cookie cleared)");
        return ResponseEntity.status(HttpStatus.NO_CONTENT)
            .header(HttpHeaders.SET_COOKIE, clear.toString())
            .build();
    }
}
