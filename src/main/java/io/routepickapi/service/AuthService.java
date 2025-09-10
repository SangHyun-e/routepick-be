package io.routepickapi.service;

import io.routepickapi.common.error.CustomException;
import io.routepickapi.common.error.ErrorType;
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

    // 로그인: 이메일로 유저 조회 -> 패스워드 매칭 -> JWT 발급
    public LoginResponse login(LoginRequest req) {
        // 1) ACTIVE 사용자만 로그인 허용
        User user = userRepository.findByEmailAndStatus(req.email(), UserStatus.ACTIVE)
            .orElseThrow(
                () -> new CustomException(ErrorType.AUTH_INVALID_CREDENTIALS));

        // 2) 패스워드 매칭 (평문 vs 해시) 불일치 -> 401
        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new CustomException(ErrorType.AUTH_INVALID_CREDENTIALS);
        }

        // 3) 액세스 토큰 발급 (subject = userId, claim = email)
        String token = jwtProvider.generateAccessToken(user.getId(), user.getEmail());

        // 4) 남은 유효시간(초) 계산 후 응답
        long expiresInSec = jwtProvider.getRemainingMillis(token) / 1000;

        log.info("User logged in: id={}, email={}", user.getId(), user.getEmail());
        return new LoginResponse(token, expiresInSec);
    }
}
