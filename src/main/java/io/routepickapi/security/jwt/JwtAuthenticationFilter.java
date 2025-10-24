package io.routepickapi.security.jwt;

import io.routepickapi.entity.user.UserStatus;
import io.routepickapi.repository.UserRepository;
import io.routepickapi.security.AuthUser;
import io.routepickapi.service.AccessTokenBlacklistService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * - Authorization: Bearer <token> 에서 토큰 추출
 * - 검증 통과 시 DB 에서 ACTIVE 유저 조회
 * - SecurityContext 에 인증 정보 세팅
 * - 실패/없음: 다음 필터로 패스(익명)
 */

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {


    private final JwtProvider jwtProvider;
    private final UserRepository userRepository;
    private final AccessTokenBlacklistService blacklistService;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        if (path.startsWith("/auth/")) {
            return true;
        }
        if (path.startsWith("/v3/api-docs")) {
            return true;
        }
        if (path.startsWith("/swagger-ui")) {
            return true;
        }
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
        FilterChain filterChain) throws ServletException, IOException {

        String auth = request.getHeader(HttpHeaders.AUTHORIZATION);

        // 1) Authorization 헤더 없거나 Bearer 아님 -> 바로 다음 필터
        if (auth == null || !auth.regionMatches(true, 0, "Bearer ", 0, 7)) {
            filterChain.doFilter(request, response);
            return;
        }

        // 2) 토큰 추출
        String token = auth.substring(7).trim();
        if (token.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        // 2-1) 빈값/쓰레기값 및 형식 가드 ('.' 2개여야 JWS)
        if ("undefined".equalsIgnoreCase(token) || "null".equalsIgnoreCase(token)) {
            filterChain.doFilter(request, response);
            return;
        }
        long dotCount = token.chars().filter(ch -> ch == '.').count();
        if (dotCount != 2) {
            filterChain.doFilter(request, response);
            return;
        }

        // 3) 블랙리스트면 인증 세팅 없이 통과
        if (blacklistService.isBlacklisted(token)) {
            log.debug("JWT blacklisted. Skip auth.");
            filterChain.doFilter(request, response);
            return;
        }

        try {
            // 4) 이미 인증 있으면 패스
            if (SecurityContextHolder.getContext().getAuthentication() != null) {
                filterChain.doFilter(request, response);
                return;
            }

            // 5) 토큰 유효성 검증 + 사용자 로드
            if (jwtProvider.validate(token)) {
                Long userId = jwtProvider.getUserId(token);
                userRepository.findByIdAndStatus(userId, UserStatus.ACTIVE).ifPresent(user -> {
                    AuthUser principal = new AuthUser(user.getId(), user.getEmail(),
                        user.getNickname());
                    UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                        principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))
                    );
                    authenticationToken.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authenticationToken);
                });
            }
        } catch (Exception e) {
            // 검증 중 오류가 나도 인증만 안 세우고 다음 필터로 넘김
            log.debug("JWT filter error", e);
        }
        filterChain.doFilter(request, response);
    }


}
