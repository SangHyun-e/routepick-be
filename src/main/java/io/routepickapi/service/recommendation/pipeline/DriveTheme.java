package io.routepickapi.service.recommendation.pipeline;

import java.util.Locale;

public enum DriveTheme {
    NATURE,
    NIGHT,
    CAFE,
    DEFAULT;

    public static DriveTheme fromRaw(String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        if (containsAny(normalized, "카페", "커피", "cafe", "coffee")) {
            return CAFE;
        }
        if (containsAny(normalized, "야경", "야간", "밤", "night", "nightview")) {
            return NIGHT;
        }
        if (containsAny(
            normalized,
            "자연",
            "숲",
            "산",
            "바다",
            "해변",
            "호수",
            "강",
            "계곡",
            "공원",
            "nature",
            "forest",
            "mountain"
        )) {
            return NATURE;
        }
        return switch (normalized) {
            case "nature", "forest", "mountain" -> NATURE;
            case "night", "nightview" -> NIGHT;
            case "cafe", "coffee" -> CAFE;
            default -> DEFAULT;
        };
    }

    private static boolean containsAny(String value, String... keywords) {
        for (String keyword : keywords) {
            if (keyword != null && !keyword.isBlank() && value.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
