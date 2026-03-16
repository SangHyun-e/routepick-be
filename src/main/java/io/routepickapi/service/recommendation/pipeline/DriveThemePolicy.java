package io.routepickapi.service.recommendation.pipeline;

import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class DriveThemePolicy {

    private static final String DEFAULT_THEME = "default";

    /**
     * TODO: DriveTheme enum/allowlist로 전환해 정책 기반 검증을 강화할 예정.
     */
    public String resolve(String rawTheme) {
        if (rawTheme == null || rawTheme.isBlank()) {
            return DEFAULT_THEME;
        }

        String normalized = rawTheme.trim().toLowerCase(Locale.ROOT);
        return normalized.isBlank() ? DEFAULT_THEME : normalized;
    }
}
