package io.routepickapi.service;

import io.routepickapi.common.error.CustomException;
import io.routepickapi.common.error.ErrorType;
import io.routepickapi.entity.user.User;
import io.routepickapi.entity.user.UserStatus;
import io.routepickapi.repository.UserRepository;
import java.security.SecureRandom;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class EmailVerificationService {

    private static final String KEY_PREFIX = "rp:email:verify:";
    private static final String TRIES_PREFIX = "rp:email:verify:tries:";
    private final StringRedisTemplate redisTemplate;
    private final UserRepository userRepository;
    private final EmailSender emailSender;
    private final SecureRandom random = new SecureRandom();

    @Value("${app.auth.email-verify.ttl-seconds:600}")
    private long ttlSeconds;

    @Value("${app.auth.email-verify.code-length:6}")
    private int codeLength;

    @Value("${app.auth.email-verify.max-tries:5}")
    private int maxTries;

    /*
     * 인증코드 발급 + Redis 저장
     * - PENDING 사용자
     */
    public void sendCode(String emailRaw) {
        String email = normalizeEmail(emailRaw);

        // 이미 인증된 이메일이면 발급 불가
        if (userRepository.existsByEmailAndStatus(email, UserStatus.ACTIVE)) {
            throw new CustomException(ErrorType.USER_EMAIL_ALREADY_VERIFIED);
        }

        // PENDING 사용자만 인증코드 발급
        User user = userRepository.findByEmailAndStatus(email, UserStatus.PENDING)
            .orElseThrow(() -> new CustomException(ErrorType.USER_NOT_FOUND));

        String code = generateNumericCode(codeLength);
        String key = KEY_PREFIX + email;
        String triesKey = TRIES_PREFIX + email;

        redisTemplate.opsForValue().set(key, code, Duration.ofSeconds(ttlSeconds));
        redisTemplate.delete(triesKey);

        emailSender.sendVerificationCode(user, code, ttlSeconds);
    }

    /**
     * 코드 검증 성공 시 ACTIVE 전환 + Redis 제거
     * - 코드 없으면(만료/미발급): EXPIRED
     * - 코드 틀리면 tries++ 후 INVALID / TOO_MANY_TRIES
     */
    public void confirmCode(String emailRaw, String codeRaw) {
        String email = normalizeEmail(emailRaw);
        String code = normalizeCode(codeRaw);

        // 이미 ACTIVE면 굳이 인증할 필요 없음
        if (userRepository.existsByEmailAndStatus(email, UserStatus.ACTIVE)) {
            throw new CustomException(ErrorType.USER_EMAIL_ALREADY_VERIFIED);
        }

        String key = KEY_PREFIX + email;
        String stored = redisTemplate.opsForValue().get(key);

        // 만료/미발급
        if (stored == null) {
            throw new CustomException(ErrorType.AUTH_EMAIL_VERIFY_CODE_EXPIRED);
        }

        // 먼저 비교: 틀렸을 때만 tries 증가
        if (!stored.equals(code)) {
            long tries = increaseTriesAndCheckLimit(email);
            log.warn("[EMAIL_VERIFY] code mismatch: email={}, tries={}/{}", email, tries, maxTries);
            throw new CustomException(ErrorType.AUTH_EMAIL_VERIFY_CODE_INVALID);
        }

        // 성공: PENDING → ACTIVE
        User user = userRepository.findByEmailAndStatus(email, UserStatus.PENDING)
            .orElseThrow(() -> new CustomException(ErrorType.USER_NOT_FOUND));

        user.activate();

        // 코드/tries 삭제
        String triesKey = TRIES_PREFIX + email;
        redisTemplate.delete(key);
        redisTemplate.delete(triesKey);

        log.info("[EMAIL_VERIFY] verified success: userId={}, email={}", user.getId(),
            user.getEmail());
    }

    /**
     * tries 증가 + ttl 동기화 + limit 초과 시 코드 폐기
     *
     * @return 현재 tries
     */
    private long increaseTriesAndCheckLimit(String normalizedEmail) {
        String triesKey = TRIES_PREFIX + normalizedEmail;

        Long triesObj = redisTemplate.opsForValue().increment(triesKey);
        long tries = triesObj == null ? 0L : triesObj;

        // tries 키도 인증 TTL에 맞춰 만료되게
        if (tries == 1L) {
            redisTemplate.expire(triesKey, Duration.ofSeconds(ttlSeconds));
        }

        if (tries > maxTries) {
            // too many tries면 코드 폐기
            String key = KEY_PREFIX + normalizedEmail;
            redisTemplate.delete(key);
            redisTemplate.delete(triesKey);
            throw new CustomException(ErrorType.AUTH_EMAIL_VERIFY_TOO_MANY_TRIES);
        }

        return tries;
    }

    private String normalizeEmail(String email) {
        if (email == null) {
            return "";
        }
        return email.trim().toLowerCase();
    }

    private String normalizeCode(String code) {
        if (code == null) {
            return "";
        }
        return code.trim();
    }

    private String generateNumericCode(int length) {
        int bound = 1;
        for (int i = 0; i < length; i++) {
            bound *= 10;
        }

        int n = random.nextInt(bound);
        String s = Integer.toString(n);

        // 왼쪽 0 padding
        return "0".repeat(Math.max(0, length - s.length())) + s;
    }
}
