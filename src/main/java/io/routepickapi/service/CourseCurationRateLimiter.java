package io.routepickapi.service;

import io.routepickapi.common.error.CustomException;
import io.routepickapi.common.error.ErrorType;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CourseCurationRateLimiter {

    private static final Duration WINDOW = Duration.ofDays(1);

    private final ConcurrentHashMap<String, RateLimitBucket> buckets = new ConcurrentHashMap<>();

    @Value("${llm.curation.daily-limit:5}")
    private int dailyLimit;

    public void validate(String key) {
        if (key == null || key.isBlank() || dailyLimit <= 0) {
            return;
        }

        RateLimitBucket bucket = buckets.compute(key, (ignored, existing) -> {
            Instant now = Instant.now();
            if (existing == null || existing.isExpired(now, WINDOW)) {
                return new RateLimitBucket(now, 1);
            }
            return existing.increment();
        });

        if (bucket.count() > dailyLimit) {
            throw new CustomException(ErrorType.COMMON_RATE_LIMIT,
                "크루저 큐레이션 요청이 너무 많습니다. 잠시 후 다시 시도해주세요.");
        }
    }

    private record RateLimitBucket(Instant startedAt, int count) {
        RateLimitBucket increment() {
            return new RateLimitBucket(startedAt, count + 1);
        }

        boolean isExpired(Instant now, Duration window) {
            return Duration.between(startedAt, now).compareTo(window) >= 0;
        }
    }
}
