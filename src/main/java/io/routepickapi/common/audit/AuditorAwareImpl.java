package io.routepickapi.common.audit;

import java.util.Optional;
import org.springframework.data.domain.AuditorAware;

/**
 * 현재 사용자 (작성 및 수정자) 식별자 제공
 * 직므은 로그인 및 보안 (Spring Security)이 없으므로 "system"으로 고정
 * 나중에 Security 붙이면 SecurityContext에서 사용자명을 꺼내 반환하도록 수정 예정
 */
public class AuditorAwareImpl implements AuditorAware<String> {

    @Override
    public Optional<String> getCurrentAuditor() {
        return Optional.of("system");
    }
}
