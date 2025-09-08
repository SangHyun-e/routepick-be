package io.routepickapi.dto.auth;

// 로그인 응답: 액세스 토큰과 만료(초)
public record LoginResponse(
    String accessToken, // "Bearer " 없이 토큰만 반환
    long expiresIn // 남은 유효시간 - 클라이언트 타이머용
) {

}
