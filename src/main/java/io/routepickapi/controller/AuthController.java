package io.routepickapi.controller;

import io.routepickapi.dto.auth.LoginRequest;
import io.routepickapi.dto.auth.LoginResponse;
import io.routepickapi.dto.auth.SignUpRequest;
import io.routepickapi.dto.auth.SignUpResponse;
import io.routepickapi.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/auth")
@Validated
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "회원가입", description = "이메일/비밀번호/닉네임 회원가입")
    @PostMapping("/signup")
    public ResponseEntity<SignUpResponse> signUp(@Valid @RequestBody SignUpRequest req) {
        SignUpResponse res = authService.signUp(req);
        return ResponseEntity.created(URI.create("/users/" + res.id())).body(res);
    }

    @Operation(summary = "로그인", description = "이메일/비밀번호 로그인 -> JWT 발급")
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest req) {
        LoginResponse res = authService.login(req);
        return ResponseEntity.ok(res); // 200 OK, body 에 토큰/만료
    }
}
