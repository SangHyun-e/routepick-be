package io.routepickapi.service;

import io.routepickapi.common.error.CustomException;
import io.routepickapi.common.error.ErrorType;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CourseExplainUsageService {

    private static final int DAILY_LIMIT = 3;
    private static final Duration TTL = Duration.ofDays(1);
    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String KEY_PREFIX = "llm:explain";

    private final StringRedisTemplate redisTemplate;

    public int consume(long userId) {
        if (userId <= 0) {
            throw new CustomException(ErrorType.COMMON_INVALID_INPUT, "사용자 정보가 필요합니다.");
        }

        String key = buildKey(userId);
        Long count = redisTemplate.opsForValue().increment(key);
        long current = count == null ? 0L : count;

        if (current == 1L) {
            redisTemplate.expire(key, TTL);
        }

        if (current > DAILY_LIMIT) {
            throw new CustomException(ErrorType.COMMON_RATE_LIMIT, "오늘 AI 설명은 모두 사용했어요");
        }

        return Math.max(0, DAILY_LIMIT - (int) current);
    }

    private String buildKey(long userId) {
        String date = LocalDate.now(KOREA_ZONE).format(DATE_FORMAT);
        return String.format("%s:%d:%s", KEY_PREFIX, userId, date);
    }
}
