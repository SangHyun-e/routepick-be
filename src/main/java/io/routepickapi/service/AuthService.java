package io.routepickapi.service;

import io.routepickapi.common.error.CustomException;
import io.routepickapi.common.error.ErrorType;
import io.routepickapi.dto.IssuedTokens;
import io.routepickapi.dto.auth.LoginRequest;
import io.routepickapi.dto.auth.LoginResponse;
import io.routepickapi.dto.auth.SignUpRequest;
import io.routepickapi.dto.auth.SignUpResponse;
import io.routepickapi.entity.user.User;
import io.routepickapi.entity.user.UserStatus;
import io.routepickapi.repository.UserRepository;
import io.routepickapi.security.jwt.JwtProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final RefreshTokenService refreshTokenService; // Redis Store

    // 회원가입
    public SignUpResponse signUp(SignUpRequest req) {
        // 중복 이메일 409 CONFLICT
        if (userRepository.existsByEmail(req.email())) {
            throw new CustomException(ErrorType.USER_EMAIL_EXISTS);
        }

        String hash = passwordEncoder.encode(req.password());
        User user = new User(req.email(), hash, req.nickname());
        Long id = userRepository.save(user).getId();

        log.info("User signed up: id={}, email={}", id, user.getEmail());
        return new SignUpResponse(id, user.getEmail(), user.getNickname());
    }

    /**
     * 로그인: 이메일/비번 검증 → access & refresh 동시 발급
     * - refresh 는 Redis 에 저장하고, 컨트롤러가 HttpOnly 쿠키로 내려줌
     */
    public IssuedTokens loginIssueTokens(LoginRequest req) {
        // 1) ACTIVE 사용자만 허용
        User user = userRepository.findByEmailAndStatus(req.email(), UserStatus.ACTIVE)
            .orElseThrow(() -> new CustomException(ErrorType.AUTH_INVALID_CREDENTIALS));

        // 2) 비밀번호 매칭
        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new CustomException(ErrorType.AUTH_INVALID_CREDENTIALS);
        }

        // 3) 액세스 토큰 발급
        String accessToken = jwtProvider.generateAccessToken(user.getId(), user.getEmail());
        long accessExpiresInSec = jwtProvider.getRemainingMillis(accessToken) / 1000;

        // 4) 리프레시 토큰 발급 (토큰 ID 포함) + Redis 저장
        String tokenId = jwtProvider.newTokenId();
        String refreshToken = jwtProvider.generateRefreshToken(user.getId(), tokenId);
        long refreshTtlSec = jwtProvider.getRemainingMillis(refreshToken) / 1000;
        refreshTokenService.save(user.getId(), tokenId, refreshToken);

        log.info("User logged in: id={}, email={}", user.getId(), user.getEmail());
        return new IssuedTokens(
            accessToken,
            accessExpiresInSec,
            refreshToken,
            refreshTtlSec,
            tokenId
        );
    }

    // 호환용 래퍼(임시: 바디엔 access만, refresh는 컨트롤러에서 쿠키로 세팅
    @Deprecated // FE/테스트 정리 후 삭제 예정
    public LoginResponse login(LoginRequest req) {
        IssuedTokens t = loginIssueTokens(req);
        return new LoginResponse(t.accessToken(), t.accessExpiresInSec());
    }
}
