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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PasswordResetService {

    private static final String KEY_PREFIX = "rp:pwd:reset:";
    private static final String TRIES_PREFIX = "rp:pwd:reset:tries:";

    private final StringRedisTemplate redisTemplate;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailSenderService emailSender;
    private final SecureRandom random = new SecureRandom();

    @Value("${app.auth.password-reset.ttl-seconds:600}")
    private long ttlSeconds;

    @Value("${app.auth.password-reset.code-length:6}")
    private int codeLength;

    @Value("${app.auth.password-reset.max-tries:5}")
    private int maxTries;

    public void requestReset(String emailRaw) {
        String email = normalizeEmail(emailRaw);

        userRepository.findByEmailAndStatus(email, UserStatus.ACTIVE).ifPresent(user -> {
            String code = generateNumericCode(codeLength);
            String key = KEY_PREFIX + email;
            String triesKey = TRIES_PREFIX + email;

            redisTemplate.opsForValue().set(key, code, Duration.ofSeconds(ttlSeconds));
            redisTemplate.delete(triesKey);

            emailSender.sendPasswordResetCode(user, code, ttlSeconds);
            log.info("[PASSWORD_RESET] code issued: userId={}, email={}", user.getId(),
                user.getEmail());
        });
    }

    public void confirmReset(String emailRaw, String codeRaw, String newPassword) {
        String email = normalizeEmail(emailRaw);
        String code = normalizeCode(codeRaw);

        String key = KEY_PREFIX + email;
        String stored = redisTemplate.opsForValue().get(key);

        if (stored == null) {
            throw new CustomException(ErrorType.AUTH_PASSWORD_RESET_CODE_EXPIRED);
        }

        if (!stored.equals(code)) {
            long tries = increaseTriesAndCheckLimit(email);
            log.warn("[PASSWORD_RESET] code mismatch: email={}, tries={}/{}", email, tries,
                maxTries);
            throw new CustomException(ErrorType.AUTH_PASSWORD_RESET_CODE_INVALID);
        }

        User user = userRepository.findByEmailAndStatus(email, UserStatus.ACTIVE)
            .orElseThrow(() -> new CustomException(ErrorType.AUTH_PASSWORD_RESET_CODE_EXPIRED));

        user.setPasswordHash(passwordEncoder.encode(newPassword));

        String triesKey = TRIES_PREFIX + email;
        redisTemplate.delete(key);
        redisTemplate.delete(triesKey);

        log.info("[PASSWORD_RESET] password updated: userId={}, email={}", user.getId(),
            user.getEmail());
    }

    private long increaseTriesAndCheckLimit(String normalizedEmail) {
        String triesKey = TRIES_PREFIX + normalizedEmail;

        Long triesObj = redisTemplate.opsForValue().increment(triesKey);
        long tries = triesObj == null ? 0L : triesObj;

        if (tries == 1L) {
            redisTemplate.expire(triesKey, Duration.ofSeconds(ttlSeconds));
        }

        if (tries > maxTries) {
            String key = KEY_PREFIX + normalizedEmail;
            redisTemplate.delete(key);
            redisTemplate.delete(triesKey);
            throw new CustomException(ErrorType.AUTH_PASSWORD_RESET_TOO_MANY_TRIES);
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

        return "0".repeat(Math.max(0, length - s.length())) + s;
    }
}
