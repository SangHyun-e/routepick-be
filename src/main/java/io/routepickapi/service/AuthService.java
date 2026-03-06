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
    private final AccessTokenBlacklistService blacklistService;
    private final EmailVerificationService emailVerificationService;
    private final UserRejoinRestrictionService rejoinRestrictionService;
    private final LoginRateLimitService loginRateLimitService;

    // 회원가입
    public SignUpResponse signUp(SignUpRequest req) {
        log.debug("SignUp requested (email={}, nickname={})", req.email(), req.nickname());
        // 중복 이메일 409 CONFLICT
        if (userRepository.existsByEmail(req.email())) {
            log.warn("SignUp failed: email already exists (email={})", req.email());
            throw new CustomException(ErrorType.USER_EMAIL_EXISTS);
        }

        rejoinRestrictionService.validateRejoinAllowed(req.email());

        if (userRepository.existsByNickname(req.nickname())) {
            log.warn("SignUp failed: nickname already exists (nickname={})", req.nickname());
            throw new CustomException(ErrorType.USER_NICKNAME_EXISTS);
        }

        String hash = passwordEncoder.encode(req.password());
        User user = new User(req.email(), hash, req.nickname());
        Long id = userRepository.save(user).getId();
        emailVerificationService.sendCode(req.email());

        log.info("SignUp success: id={}, email={}, status={}", id, user.getEmail(),
            user.getStatus());
        return new SignUpResponse(id, user.getEmail(), user.getNickname());
    }

    /**
     * 로그인: 이메일/비번 검증 → access & refresh 동시 발급
     * - refresh 는 Redis 에 저장하고, 컨트롤러가 HttpOnly 쿠키로 내려줌
     */
    public IssuedTokens loginIssueTokens(LoginRequest req) {
        log.debug("Login requested (email={})", req.email());
        String normalizedEmail = loginRateLimitService.normalizeEmail(req.email());
        loginRateLimitService.ensureNotRateLimited(normalizedEmail);

        try {
            // 1) 사용자 확인 (PENDING 허용)
            User user = userRepository.findByEmail(req.email())
                .orElseThrow(() -> {
                    log.warn("Login failed: user not found (email={})", req.email());
                    return new CustomException(ErrorType.AUTH_INVALID_CREDENTIALS);
                });

            validateLoginableUser(user);

            // 2) 비밀번호 매칭
            if (user.getPasswordHash() == null) {
                log.warn("Login failed: password not set (email={})", req.email());
                throw new CustomException(ErrorType.AUTH_INVALID_CREDENTIALS);
            }

            if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
                log.warn("Login failed: wrong password (email={})", req.email());
                throw new CustomException(ErrorType.AUTH_INVALID_CREDENTIALS);
            }

            IssuedTokens tokens = issueTokens(user);
            loginRateLimitService.reset(normalizedEmail);
            return tokens;
        } catch (CustomException e) {
            if (e.getType() == ErrorType.AUTH_INVALID_CREDENTIALS
                && loginRateLimitService.recordFailure(normalizedEmail)) {
                throw new CustomException(ErrorType.AUTH_LOGIN_RATE_LIMIT);
            }
            throw e;
        }
    }

    public IssuedTokens issueTokensForUser(User user) {
        validateLoginableUser(user);
        return issueTokens(user);
    }

    public void validateLoginableUser(User user) {
        if (user.getStatus() == UserStatus.ACTIVE) {
            return;
        }
        if (user.getStatus() == UserStatus.PENDING) {
            log.warn("Login failed: user pending (email={})", user.getEmail());
            throw new CustomException(ErrorType.USER_EMAIL_NOT_VERIFIED);
        }
        if (user.getStatus() == UserStatus.BLOCKED) {
            log.warn("Login failed: user blocked (email={})", user.getEmail());
            throw new CustomException(ErrorType.USER_BLOCKED);
        }
        if (user.getStatus() == UserStatus.DELETED) {
            log.warn("Login failed: user deleted (email={})", user.getEmail());
            throw new CustomException(ErrorType.AUTH_INVALID_CREDENTIALS);
        }
    }

    private IssuedTokens issueTokens(User user) {
        // 3) 액세스 토큰 발급
        String accessToken = jwtProvider.generateAccessToken(user.getId(), user.getEmail());
        long accessExpiresInSec = jwtProvider.getRemainingMillis(accessToken) / 1000;

        // 4) 리프레시 토큰 발급 (토큰 ID 포함) + Redis 저장
        String tokenId = jwtProvider.newTokenId();
        String refreshToken = jwtProvider.generateRefreshToken(user.getId(), tokenId);
        long refreshTtlSec = jwtProvider.getRemainingMillis(refreshToken) / 1000;
        refreshTokenService.save(user.getId(), tokenId, refreshToken);

        log.info("Login success: id={}, email={}, tid={}", user.getId(), user.getEmail(), tokenId);
        return new IssuedTokens(
            accessToken,
            accessExpiresInSec,
            refreshToken,
            refreshTtlSec,
            tokenId
        );
    }

    // 호환용 래퍼(임시: 바디엔 access 만, refresh 는 컨트롤러에서 쿠키로 세팅
    @Deprecated // FE/테스트 정리 후 삭제 예정
    public LoginResponse login(LoginRequest req) {
        IssuedTokens t = loginIssueTokens(req);
        return new LoginResponse(t.accessToken(), t.accessExpiresInSec());
    }

    public IssuedTokens refresh(String refreshToken) {
        log.debug("Refresh requested");
        // 1) 형식/서명/만료 검증 + refresh 타입 확인
        if (!jwtProvider.validate(refreshToken) || !jwtProvider.isRefreshToken(refreshToken)) {
            log.warn("Refresh failed: invalid or non-refresh token");
            throw new CustomException(ErrorType.AUTH_TOKEN_INVALID);
        }

        Long userId = jwtProvider.getUserId(refreshToken);
        String oldTid = jwtProvider.getTokenId(refreshToken);

        // 2) Redis 에 실제 보관중인지 확인 (도난/재사용 방지)
        String stored = refreshTokenService.get(userId, oldTid);
        if (stored == null || !stored.equals(refreshToken)) {
            log.warn("Refresh failed: token not stored or mismatched (userId={}, tid={})", userId,
                oldTid);
            throw new CustomException(ErrorType.AUTH_TOKEN_INVALID);
        }

        // 3) 새 Access 발급
        User user = userRepository.findById(userId)
            .orElseThrow(() -> {
                log.warn("Refresh failed: user not found (userId={})", userId);
                return new CustomException(ErrorType.USER_NOT_FOUND);
            });

        validateLoginableUser(user);

        String newAccess = jwtProvider.generateAccessToken(user.getId(), user.getEmail());
        long accessExpSec = jwtProvider.getRemainingMillis(newAccess) / 1000;

        // 4) refresh 토큰 회전: 새 tid/refresh 발급 + Redis 교체
        String newTid = jwtProvider.newTokenId();
        String newRefresh = jwtProvider.generateRefreshToken(userId, newTid);
        long refreshTtlSec = jwtProvider.getRemainingMillis(newRefresh) / 1000;

        refreshTokenService.delete(userId, oldTid);
        refreshTokenService.save(userId, newTid, newRefresh);

        log.info("Refresh success: userId={}, newTid={}, accessExpSec={}, refreshTtlSec={}", userId,
            newTid, accessExpSec, refreshTtlSec);
        return new IssuedTokens(newAccess, accessExpSec, newRefresh, refreshTtlSec, newTid);
    }

    public void logout(String accessHeader, String refreshCookie) {
        boolean hasAccess =
            accessHeader != null && accessHeader.regionMatches(true, 0, "Bearer ", 0, 7);
        boolean hasRefresh = refreshCookie != null && !refreshCookie.isBlank();
        log.debug("Logout requested (hasAccessHeader={}, hasRefreshCookie={})", hasAccess,
            hasRefresh);

        // 1) access 블랙리스트 등록
        if (hasAccess) {
            String at = accessHeader.substring(7).trim();
            if (!at.isEmpty() && jwtProvider.validate(at)) {
                long ttlMs = jwtProvider.getRemainingMillis(at);
                blacklistService.blacklist(at, ttlMs);
                log.info("Access token blacklisted (ttlMs={})", ttlMs);
            } else {
                log.debug("Skip blacklist: invalid or empty access token");
            }
        }

        // 2) refresh token 삭제
        if (hasRefresh && jwtProvider.validate(refreshCookie)
            && jwtProvider.isRefreshToken(refreshCookie)) {
            Long uid = jwtProvider.getUserId(refreshCookie);
            String tid = jwtProvider.getTokenId(refreshCookie);
            refreshTokenService.delete(uid, tid);
            log.info("Refresh token deleted (userId={}, tid={})", uid, tid);
        } else if (hasRefresh) {
            log.debug("Skip refresh delete: invalid or non-refresh token");
        }
        log.info("Logout completed");
    }

    public void logoutAll(String accessHeader, long userId) {
        boolean hasAccess =
            accessHeader != null && accessHeader.regionMatches(true, 0, "Bearer ", 0, 7);
        log.debug("Logout ALL requested (userId={}, hasAccessHeader={})", userId, hasAccess);

        // 1) access 블랙리스트
        if (hasAccess) {
            String at = accessHeader.substring(7).trim();
            if (!at.isEmpty() && jwtProvider.validate(at)) {
                long ttlMs = jwtProvider.getRemainingMillis(at);
                blacklistService.blacklist(at, ttlMs);
                log.info("Access token blacklisted for logout-all (ttlMs={})", ttlMs);
            } else {
                log.debug("Skip blacklist in logout-all: invalid/empty access");
            }
        }

        // 2) 해당 유저의 모든 refresh 폐기
        refreshTokenService.deleteAllForUser(userId);
        log.info("All refresh tokens deleted (userId={})", userId);
    }
}
