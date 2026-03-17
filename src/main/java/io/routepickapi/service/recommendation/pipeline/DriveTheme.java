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
        return switch (normalized) {
            case "nature", "forest", "mountain" -> NATURE;
            case "night", "nightview" -> NIGHT;
            case "cafe", "coffee" -> CAFE;
            default -> DEFAULT;
        };
    }
}
