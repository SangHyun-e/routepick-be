package io.routepickapi.service;

import io.routepickapi.common.error.CustomException;
import io.routepickapi.common.error.ErrorType;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LoginRateLimitService {

    private static final String KEY_PREFIX = "login_fail:";
    private static final int MAX_FAILURES = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(5);

    private final StringRedisTemplate redisTemplate;

    public void ensureNotRateLimited(String normalizedEmail) {
        String key = buildKey(normalizedEmail);
        String value = redisTemplate.opsForValue().get(key);
        if (value == null) {
            return;
        }
        try {
            if (Long.parseLong(value) >= MAX_FAILURES) {
                throw new CustomException(ErrorType.AUTH_LOGIN_RATE_LIMIT);
            }
        } catch (NumberFormatException ex) {
            redisTemplate.delete(key);
        }
    }

    public boolean recordFailure(String normalizedEmail) {
        String key = buildKey(normalizedEmail);
        Long count = redisTemplate.opsForValue().increment(key);
        long current = count == null ? 0L : count;

        if (current == 1L) {
            redisTemplate.expire(key, LOCK_DURATION);
        }

        if (current >= MAX_FAILURES) {
            redisTemplate.expire(key, LOCK_DURATION);
            return true;
        }

        return false;
    }

    public void reset(String normalizedEmail) {
        redisTemplate.delete(buildKey(normalizedEmail));
    }

    public String normalizeEmail(String email) {
        if (email == null) {
            return "";
        }
        return email.trim().toLowerCase();
    }

    private String buildKey(String normalizedEmail) {
        if (normalizedEmail == null) {
            return KEY_PREFIX;
        }
        return KEY_PREFIX + normalizedEmail;
    }
}
