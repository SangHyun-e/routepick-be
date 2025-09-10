package io.routepickapi.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 액세스 토큰 블랙리스트
 * - Key: bl:at:{sha256(token)} 토큰 원문 대신 해시 사용
 * - Val: "1"
 * - TTL: 액세스 토큰의 남은 유효시간(ms)
 */

@Component
@RequiredArgsConstructor
public class AccessTokenBlacklistService {

    private static final HexFormat HEX = HexFormat.of(); // 소문자 hex(기본)

    private final StringRedisTemplate redis;

    // 블랙리스트 키 규약
    private String key(String tokenHash) {
        return "bl:at:" + tokenHash;
    }

    public void blacklist(String accessToken, long ttlMillis) {
        if (ttlMillis <= 0L) {
            return;
        }

        String tokenHash = sha256Hex(accessToken);
        Duration ttl = Duration.ofMillis(ttlMillis);
        redis.opsForValue().set(key(tokenHash), "1", ttl);
    }

    public boolean isBlacklisted(String accessToken) {
        String tokenHash = sha256Hex(accessToken);
        Boolean exists = redis.hasKey(key(tokenHash));
        return Boolean.TRUE.equals(exists);
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HEX.formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not supported", e);
        }
    }
}
