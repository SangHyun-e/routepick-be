package io.routepickapi.dto.course;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

public enum DriveMood {
    NIGHT_VIEW("야경", List.of("야경", "전망", "전망대", "루프탑", "야경포인트")),
    EMOTIONAL("감성", List.of("감성", "무드", "포토", "뷰", "테라스")),
    HEALING("힐링", List.of("힐링", "자연", "숲", "공원", "호수", "수목원")),
    QUIET("한적한", List.of("한적", "조용", "산책", "숲", "공원"));

    private final String label;
    private final List<String> keywords;

    DriveMood(String label, List<String> keywords) {
        this.label = label;
        this.keywords = keywords;
    }

    public String label() {
        return label;
    }

    public List<String> keywords() {
        return keywords;
    }

    public static List<DriveMood> fromLabels(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }

        return raw.stream()
            .filter(Objects::nonNull)
            .map(value -> value.trim().toLowerCase(Locale.ROOT))
            .flatMap(value -> Arrays.stream(values())
                .filter(mood -> mood.label.toLowerCase(Locale.ROOT).equals(value)))
            .distinct()
            .collect(Collectors.toList());
    }
}
