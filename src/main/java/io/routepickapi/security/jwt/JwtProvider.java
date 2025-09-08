package io.routepickapi.security.jwt;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SecurityException;
import io.routepickapi.config.JwtProperties;
import jakarta.annotation.PostConstruct;
import java.util.Date;
import javax.crypto.SecretKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * JWT 발급/검증/파싱 전담 컴포넌트
 * - subject: userId (문자열)
 * - claim: email (선택적으로 확장)
 * - issuer / ttl: application.yml 의 jwt.* 로부터 주입
 */

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtProvider {

    private final JwtProperties props;
    private SecretKey secretKey; // 서명/검증에 사용할 HMAC 키

    @PostConstruct
    void init() {
        byte[] keyBytes = Decoders.BASE64.decode(props.getSecret());
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * 액세스 토큰 발급
     *
     * @Param userId 내부 PK
     * @Param email 보조 클레임
     */
    public String generateAccessToken(Long userId, String email) {
        long now = System.currentTimeMillis();
        Date iat = new Date(now);
        Date exp = new Date(now + props.getAccessTtlSeconds() * 1000);

        return Jwts.builder()
            .issuer(props.getIssuer())
            .subject(String.valueOf(userId))
            .issuedAt(iat)
            .expiration(exp)
            .claim("email", email)
            .signWith(secretKey)
            .compact();
    }

    // 파싱 + 서명검증 + 만료검증 (유효하면 true)
    public boolean validate(String token) {
        try {
            Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token);
            return true;
        } catch (SecurityException | MalformedJwtException e) {
            log.debug("잘못된 서명/형식의 JWT.", e);
        } catch (ExpiredJwtException e) {
            log.debug("만료된 JWT.", e);
        } catch (UnsupportedJwtException e) {
            log.debug("지원되지 않는 JWT.", e);
        } catch (IllegalArgumentException e) {
            log.debug("비어있거나 손상된 JWT.", e);
        }
        return false;
    }

    // 토큰에서 userId(sub) 추출
    public Long getUserId(String token) {
        String sub = Jwts.parser().verifyWith(secretKey).build()
            .parseSignedClaims(token)
            .getPayload()
            .getSubject();
        return Long.parseLong(sub);
    }

    // 토큰에서 email(claim) 추출
    public String getEmail(String token) {
        return Jwts.parser().verifyWith(secretKey).build()
            .parseSignedClaims(token)
            .getPayload()
            .get("email", String.class);
    }

    // 남은 유효시간(ms)
    public long getRemainingMillis(String token) {
        Date exp = Jwts.parser().verifyWith(secretKey).build()
            .parseSignedClaims(token)
            .getPayload()
            .getExpiration();
        return exp.getTime() - System.currentTimeMillis();
    }
}
