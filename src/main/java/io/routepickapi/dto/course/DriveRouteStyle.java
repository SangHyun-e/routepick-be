package io.routepickapi.dto.course;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

public enum DriveRouteStyle {
    COASTAL("해안길", List.of("해안", "해안도로", "해안길", "바닷길", "바다", "등대"),
        List.of("수산", "어시장", "수산시장", "시장", "마트", "회", "횟집", "건어물", "활어")),
    MOUNTAIN_ROAD("산길", List.of("산길", "산도로", "고개", "산악", "계곡길"), List.of()),
    WINDING("와인딩", List.of("와인딩", "굽이", "커브", "S자"), List.of()),
    NORMAL("무난한", List.of(), List.of());

    private final String label;
    private final List<String> keywords;
    private final List<String> blockedKeywords;

    DriveRouteStyle(String label, List<String> keywords, List<String> blockedKeywords) {
        this.label = label;
        this.keywords = keywords;
        this.blockedKeywords = blockedKeywords;
    }

    public String label() {
        return label;
    }

    public List<String> keywords() {
        return keywords;
    }

    public List<String> blockedKeywords() {
        return blockedKeywords;
    }

    public static List<DriveRouteStyle> fromLabels(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }

        return raw.stream()
            .filter(Objects::nonNull)
            .map(value -> value.trim().toLowerCase(Locale.ROOT))
            .flatMap(value -> Arrays.stream(values())
                .filter(style -> style.label.toLowerCase(Locale.ROOT).equals(value)))
            .distinct()
            .collect(Collectors.toList());
    }
}
