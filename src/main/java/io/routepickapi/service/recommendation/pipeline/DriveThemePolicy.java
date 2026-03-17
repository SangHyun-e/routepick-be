package io.routepickapi.service.recommendation.pipeline;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class DriveThemePolicy {

    private static final String DEFAULT_THEME_LABEL = "맞춤 추천";
    private static final String DRIVE_SUFFIX = "드라이브";

    private static final Map<String, String> TOKEN_LABELS = Map.ofEntries(
        Map.entry("night", "야경"),
        Map.entry("nightview", "야경"),
        Map.entry("mood", "감성"),
        Map.entry("heal", "힐링"),
        Map.entry("calm", "한적한"),
        Map.entry("cafe", "카페"),
        Map.entry("food", "맛집"),
        Map.entry("gourmet", "맛집"),
        Map.entry("view", "전망대"),
        Map.entry("scenic", "풍경"),
        Map.entry("nature", "자연"),
        Map.entry("forest", "숲"),
        Map.entry("walk", "산책"),
        Map.entry("coast", "해안길"),
        Map.entry("coastal", "해안길"),
        Map.entry("ocean", "바다"),
        Map.entry("sea", "바다"),
        Map.entry("beach", "해변"),
        Map.entry("mount", "산길"),
        Map.entry("mountain", "산길"),
        Map.entry("lake", "호수"),
        Map.entry("river", "강변"),
        Map.entry("sunset", "노을"),
        Map.entry("sunrise", "일출"),
        Map.entry("wind", "와인딩")
    );

    /**
     * TODO: DriveTheme enum/allowlist로 전환해 정책 기반 검증을 강화할 예정.
     */
    public String resolve(String rawTheme) {
        if (rawTheme == null || rawTheme.isBlank()) {
            return DEFAULT_THEME_LABEL;
        }

        String trimmed = rawTheme.trim();
        if (trimmed.isBlank()) {
            return DEFAULT_THEME_LABEL;
        }

        String normalized = trimmed.toLowerCase(Locale.ROOT);
        if ("default".equals(normalized)) {
            return DEFAULT_THEME_LABEL;
        }

        if (containsKorean(trimmed)) {
            return appendDriveSuffix(trimmed);
        }

        String[] tokens = normalized.split("[-/_]+");
        String joined = Arrays.stream(tokens)
            .map(String::trim)
            .filter(token -> !token.isBlank())
            .map(token -> TOKEN_LABELS.getOrDefault(token, ""))
            .filter(label -> !label.isBlank())
            .distinct()
            .limit(4)
            .collect(Collectors.joining(" · "));

        if (joined.isBlank()) {
            return DEFAULT_THEME_LABEL;
        }
        return appendDriveSuffix(joined);
    }

    private boolean containsKorean(String value) {
        return value.chars().anyMatch(ch -> Character.UnicodeBlock.of(ch) == Character.UnicodeBlock.HANGUL_SYLLABLES);
    }

    private String appendDriveSuffix(String label) {
        if (label == null || label.isBlank()) {
            return DEFAULT_THEME_LABEL;
        }
        if (DEFAULT_THEME_LABEL.equals(label)) {
            return label;
        }
        if (label.contains(DRIVE_SUFFIX) || label.contains("코스") || label.contains("여행")) {
            return label;
        }
        return label + " " + DRIVE_SUFFIX;
    }
}
