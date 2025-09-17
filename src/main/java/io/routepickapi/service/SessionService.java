package io.routepickapi.service;

import io.routepickapi.dto.auth.SessionInfoResponse;
import io.routepickapi.security.jwt.JwtProvider;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SessionService {

    private final RefreshTokenService refreshTokenService;
    private final JwtProvider jwtProvider;

    public List<SessionInfoResponse> listSessions(long userId) {
        Set<String> ids = refreshTokenService.tokenIds(userId);
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }

        List<SessionInfoResponse> out = new ArrayList<>();

        for (String tid : ids) {
            String token = refreshTokenService.get(userId, tid);
            if (token == null) {
                // 값이 없는데 인덱스만 남은 고아 상태 -> 청소
                refreshTokenService.removeIndex(userId, tid);
                log.debug("Orphan tid removed from index (userId={}, tid={})", userId, tid);
                continue;
            }

            Date iat = jwtProvider.getIssuedAt(token);
            Date exp = jwtProvider.getExpiration(token);
            long ttlSec = Math.max(0L, jwtProvider.getRemainingMillis(token) / 1000);

            long iatSec = (iat != null ? iat.getTime() / 1000 : 0L);
            long expSec = (exp != null ? exp.getTime() / 1000 : 0L);

            out.add(new SessionInfoResponse(tid, iatSec, expSec, ttlSec));
        }

        // 만료시간 내림차순으로 정렬(가장 오래 남은 세션이 위로)
        out.sort(Comparator.comparingLong(SessionInfoResponse::expiresAtEpochSec).reversed());
        return out;
    }

    /**
     * 현재 사용자(userId)의 특정 tid 세션 삭제 존재하지 않아도 에러 없이 통과 (멱등성)
     */
    public void revokeSession(long userId, String tokenId) {
        // 실제 값 삭제 + 인덱스에서 제거 (값이 없어도 remove는 안전)
        refreshTokenService.delete(userId, tokenId);
        log.info("Session revoked (userid-{}, tid={})", userId, tokenId);
    }
}
