package io.routepickapi.controller;

import io.routepickapi.common.error.CustomException;
import io.routepickapi.common.error.ErrorType;
import io.routepickapi.dto.IssuedTokens;
import io.routepickapi.dto.auth.EmailVerifyConfirmRequest;
import io.routepickapi.dto.auth.EmailVerifySendRequest;
import io.routepickapi.dto.auth.LoginRequest;
import io.routepickapi.dto.auth.LoginResponse;
import io.routepickapi.dto.auth.SessionInfoResponse;
import io.routepickapi.dto.auth.SignUpRequest;
import io.routepickapi.dto.auth.SignUpResponse;
import io.routepickapi.security.AuthUser;
import io.routepickapi.service.AuthService;
import io.routepickapi.service.EmailVerificationService;
import io.routepickapi.service.SessionService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
    private final SessionService sessionService;
    private final EmailVerificationService emailVerificationService;

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

    // 쿠키 삭제 로직
    private ResponseCookie clearRefreshCookie() {
        return ResponseCookie.from(REFRESH_COOKIE, "")
            .httpOnly(true)
            .secure(false)
            .sameSite("Lax")
            .path("/")
            .maxAge(Duration.ZERO)
            .build();
    }

    @Operation(summary = "회원가입", description = "이메일/비밀번호/닉네임 회원가입")
    @PostMapping("/signup")
    public ResponseEntity<SignUpResponse> signUp(@Valid @RequestBody SignUpRequest req) {
        log.debug("POST /auth/signup - request received (email={}, nickname={})", req.email(),
            req.nickname());
        SignUpResponse res = authService.signUp(req);
        log.info("User signed up: id={}, email={}", res.id(), res.email());
        return ResponseEntity.created(URI.create("/users/" + res.id())).body(res);
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

    @Operation(summary = "모든 기기에서 로그아웃", description = "현재 access 블랙리스트 + 사용자 모든 refresh 토큰 폐기 + 쿠키 삭제")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/logout-all")
    public ResponseEntity<Void> logoutAll(
        @AuthenticationPrincipal AuthUser me,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader
    ) {
        log.debug("POST /auth/logout-all - request received (userId={}, hasAuthHeader={})", me.id(),
            authHeader != null);

        authService.logoutAll(authHeader, me.id());

        ResponseCookie clear = clearRefreshCookie();
        log.info("Logout-all success (userId={}, refresh cookie cleared)", me.id());
        return ResponseEntity.status(HttpStatus.NO_CONTENT)
            .header(HttpHeaders.SET_COOKIE, clear.toString())
            .build();
    }

    @Operation(summary = "내 세션 목록", description = "현재 계정의 활성 refresh 세션 목록을 최신순으로 반환")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/sessions/")
    public ResponseEntity<List<SessionInfoResponse>> listSessions(
        @AuthenticationPrincipal AuthUser me
    ) {
        log.debug("GET /auth/sessions - userId={}", me.id());
        List<SessionInfoResponse> res = sessionService.listSessions(me.id());
        log.info("Sessions listed (userId={}, count={})", me.id(), res.size());
        return ResponseEntity.ok(res);
    }

    @Operation(summary = "특정 세션 철회", description = "tid로 지정한 refresh 세션(다른기기 포함) 철회")
    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/sessions/{tid}")
    public ResponseEntity<Void> revokeSession(
        @AuthenticationPrincipal AuthUser me,
        @PathVariable("tid") String tid
    ) {
        log.debug("DELETE /auth/sessions/{tid} - userId={}, tid={}", me.id(), tid);
        sessionService.revokeSession(me.id(), tid);
        log.info("Session revoke success (userId={}, tid={})", me.id(), tid);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "이메일 인증코드 발급", description = "PENDING 사용자에게 인증코드 발급(현재는 로그로만)")
    @PostMapping("/email/verify-code/send")
    public ResponseEntity<Void> sendEmailVerifyCode(
        @Valid @RequestBody EmailVerifySendRequest req) {
        emailVerificationService.sendCode(req.email());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "이메일 인증코드 확인", description = "코드 검증 성공 시 사용자 ACTIVE 전환")
    @PostMapping("/email/verify-code/confirm")
    public ResponseEntity<Void> confirmEmailVerifyCode(@Valid @RequestBody
    EmailVerifyConfirmRequest req) {
        emailVerificationService.confirmCode(req.email(), req.code());
        return ResponseEntity.noContent().build();
    }
}
