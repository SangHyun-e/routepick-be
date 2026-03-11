package io.routepickapi.dto.course;

import io.routepickapi.common.error.CustomException;
import io.routepickapi.common.error.ErrorType;
import java.util.List;

public enum CourseTheme {
    NIGHT_VIEW("야경", List.of("전망대", "한강", "루프탑", "카페", "야경")),
    SEA("바다", List.of("해변", "항구", "바다", "해수욕장")),
    MOUNTAIN("산", List.of("산", "공원", "전망대")),
    CAFE("카페", List.of("카페")),
    FOOD("맛집", List.of("맛집", "음식점", "식당", "레스토랑")),
    WINDING("와인딩", List.of("와인딩", "산길", "고개", "드라이브", "해안도로"));

    private final String label;
    private final List<String> keywords;

    CourseTheme(String label, List<String> keywords) {
        this.label = label;
        this.keywords = keywords;
    }

    public String label() {
        return label;
    }

    public List<String> keywords() {
        return keywords;
    }

    public static CourseTheme from(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new CustomException(ErrorType.COMMON_INVALID_INPUT, "theme은 필수입니다.");
        }

        for (CourseTheme theme : values()) {
            if (theme.label.equals(raw)) {
                return theme;
            }
        }

        throw new CustomException(ErrorType.COMMON_INVALID_INPUT, "지원하지 않는 테마입니다.");
    }
}
