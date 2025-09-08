package io.routepickapi.config;

import io.routepickapi.security.AuthUser;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
// JPA Auditing 기능 활성화 + @CreatedBy/@LastModifiedBy 값 소스 빈 이름 지정
public class JpaAuditConfig {

    @Bean // 스프링 컨테이너 빈 주입
    public AuditorAware<String> auditorAware() {
        return () -> {
            // 1) 현재 인증 정보
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();

            // 2) 인증 없음/익명이면 값 채우지 않음(null 저장)
            if (auth == null || !auth.isAuthenticated()
                || auth instanceof AnonymousAuthenticationToken) {
                return Optional.empty();
            }

            // 3) AuthUser면 id를 문자열로 저장 (ex: "123")
            Object principal = auth.getPrincipal();
            if (principal instanceof AuthUser authUser) {
                return Optional.of(String.valueOf(authUser.id()));
            }

            // 4) 예외 케이스: 다른 타입이면 Spring Security 의 name 사용 (보통 username/email)
            return Optional.ofNullable(auth.getName());
        };
    }
}
