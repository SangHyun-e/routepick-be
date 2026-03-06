package io.routepickapi.dto;

/**
 * 로그인 시 서버 내부에서만 쓰는 발급 결과 DTO
 * - refreshToken 은 응답 바디에 담지 않고, 컨트롤러에서 쿠키로 내려보냄
 */
public record IssuedTokens(
    String accessToken,        // 액세스 토큰
    long accessExpiresInSec,   // 액세스 만료(초)
    String refreshToken,       // 리프레시 토큰 (쿠키로 전송)
    long refreshTtlSec,        // 리프레시 TTL(초)
    String refreshTokenId      // Redis 저장 인덱싱용 토큰 ID(tid)
) {

}
