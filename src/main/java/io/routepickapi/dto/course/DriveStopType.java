package io.routepickapi.dto.course;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public enum DriveStopType {
    MOOD_CAFE(
        "분좋카",
        List.of("뷰카페", "루프탑", "테라스", "정원", "오션뷰", "리버뷰", "대형카페", "베이커리", "로스터리", "브런치"),
        List.of(
            "스타벅스",
            "이디야",
            "메가커피",
            "빽다방",
            "컴포즈",
            "더벤티",
            "매머드",
            "테이크아웃",
            "무인",
            "더리터",
            "커피빈",
            "탐앤탐스",
            "투썸",
            "할리스",
            "파스쿠찌",
            "엔제리너스",
            "던킨"
        ),
        Set.of("CE7")
    ),
    FOOD(
        "맛집",
        List.of("맛집", "식당", "레스토랑", "한식", "양식", "일식", "분식", "브런치"),
        List.of("시장", "마트", "수산", "어시장", "횟집", "포장마차"),
        Set.of("FD6")
    ),
    VIEWPOINT(
        "전망대",
        List.of("전망대", "전망", "스카이", "타워"),
        List.of(),
        Set.of()
    ),
    WALK(
        "산책",
        List.of("산책", "산책로", "둘레길", "공원", "숲길"),
        List.of(),
        Set.of()
    );

    private final String label;
    private final List<String> keywords;
    private final List<String> blockedKeywords;
    private final Set<String> requiredGroupCodes;

    DriveStopType(
        String label,
        List<String> keywords,
        List<String> blockedKeywords,
        Set<String> requiredGroupCodes
    ) {
        this.label = label;
        this.keywords = keywords;
        this.blockedKeywords = blockedKeywords;
        this.requiredGroupCodes = requiredGroupCodes;
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

    public Set<String> requiredGroupCodes() {
        return requiredGroupCodes;
    }

    public static List<DriveStopType> fromLabels(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }

        return raw.stream()
            .filter(Objects::nonNull)
            .map(value -> value.trim().toLowerCase(Locale.ROOT))
            .flatMap(value -> Arrays.stream(values())
                .filter(type -> type.label.toLowerCase(Locale.ROOT).equals(value)))
            .distinct()
            .collect(Collectors.toList());
    }
}
