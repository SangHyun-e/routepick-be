package io.routepickapi.config;

import io.routepickapi.common.audit.AuditorAwareImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAware") // JPA Auditing 기능 활성화 + @CreatedBy/@LastModifiedBy 값 소스 빈 이름 지정
public class JpaAuditConfig {
    @Bean // 스프링 컨테이너 빈 주입
    public AuditorAware<String> auditorAware() {
        return new AuditorAwareImpl();
    }
}
