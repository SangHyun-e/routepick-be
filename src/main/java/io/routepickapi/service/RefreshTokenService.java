package io.routepickapi.service;

import io.routepickapi.config.JwtProperties;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Refresh 토큰을 Redis 에 K/V로 보관하는 저장소
 * - Key: rt:{userId}:{tokenId}
 * - Value: refreshToken(문자열)
 * - TTL: jwt.refreshTtlSeconds
 */

@Component
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenService {

    private final StringRedisTemplate redis;
    private final JwtProperties jwtProps;

    private String key(long userId, String tokenId) {
        return "rt:%d:%s".formatted(userId, tokenId);
    }

    private String indexKey(long userId) {
        return "rtidx:%d".formatted(userId);
    }

    // 저장 rt:{userId}:{tokenId} = token (TTL 부여) + 인덱스 세트에 tokenId 추가
    public void save(long userId, String tokenId, String refreshToken) {
        Duration ttl = Duration.ofSeconds(jwtProps.getRefreshTtlSeconds());
        String k = key(userId, tokenId);
        redis.opsForValue().set(key(userId, tokenId), refreshToken, ttl);
        redis.opsForSet().add(indexKey(userId), tokenId);
        log.info("RT Save -> key={}, ttlSec={}", k, ttl.toSeconds());
    }

    // 단건 조회(검증용)
    public String get(long userId, String tokenId) {
        return redis.opsForValue().get(key(userId, tokenId));
    }

    // 단건 삭제(로그아웃 한 세션만)
    public void delete(long userId, String tokenId) {
        redis.delete(key(userId, tokenId));
        redis.opsForSet().remove(indexKey(userId), tokenId);
    }

    // 유저의 모든 refresh 토큰 제거(모든 기기 로그아웃)
    public void deleteAllForUser(long userId) {
        Set<String> tokenIds = redis.opsForSet().members(indexKey(userId));
        if (tokenIds != null && !tokenIds.isEmpty()) {
            List<String> keys = tokenIds.stream().map(tid -> key(userId, tid)).toList();
            redis.delete(keys);
        }
        redis.delete(indexKey(userId));
    }
}
