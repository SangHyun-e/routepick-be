package io.routepickapi.security.jwt;

import io.routepickapi.entity.user.UserStatus;
import io.routepickapi.repository.UserRepository;
import io.routepickapi.security.AuthUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
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
public class JwtAuthenticationFilter extends OncePerRequestFilter {


    private final JwtProvider jwtProvider;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
        FilterChain filterChain) throws ServletException, IOException {

        String auth = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (auth == null || !auth.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = auth.substring(7).trim();

        try {
            if (jwtProvider.validate(token)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
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

        }
        filterChain.doFilter(request, response);
    }
}
