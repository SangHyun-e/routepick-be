package io.routepickapi.service.recommendation.pipeline;

import io.routepickapi.domain.poi.Poi;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class PoiThemePolicy {

    private static final Set<String> NATURE_ALLOW = Set.of(
        "전망대",
        "공원",
        "산",
        "숲",
        "강변",
        "둘레길",
        "호수",
        "바다",
        "해변",
        "해안",
        "자연",
        "계곡",
        "산책",
        "산책로",
        "수목원",
        "자연휴양림"
    );

    private static final Set<String> NATURE_EXCLUDE = Set.of(
        "궁",
        "문",
        "기념물",
        "동상",
        "광장",
        "기념관"
    );

    private static final Set<String> CORE_EXCLUDE = Set.of(
        "궁",
        "문",
        "기념물",
        "동상",
        "광장",
        "기념관"
    );

    private static final Set<String> CAFE_ALLOW = Set.of(
        "카페",
        "뷰카페",
        "커피",
        "디저트",
        "루프탑",
        "오션뷰",
        "대형",
        "브런치",
        "베이커리",
        "테라스",
        "리버뷰",
        "오션"
    );

    private static final Set<String> CAFE_EXCLUDE = Set.of(
        "식당",
        "음식점",
        "맛집",
        "분식",
        "국밥",
        "치킨",
        "피자",
        "고깃집",
        "술집",
        "주점",
        "바",
        "펍",
        "포차",
        "스타벅스",
        "이디야",
        "투썸",
        "메가커피",
        "빽다방",
        "커피빈",
        "할리스",
        "탐앤탐스"
    );

    private static final Set<String> NIGHT_ALLOW = Set.of(
        "야경",
        "전망",
        "전망대",
        "조명",
        "한강",
        "드라이브",
        "야간",
        "노을",
        "일몰",
        "일출",
        "루프탑",
        "브릿지",
        "다리",
        "스카이",
        "뷰"
    );

    private static final Set<String> NIGHT_EXCLUDE = Set.of(
        "궁",
        "박물관",
        "역사",
        "기념물",
        "동상",
        "광장",
        "기념관",
        "도심"
    );

    public boolean isAllowed(Poi poi, DriveTheme theme) {
        if (poi == null || theme == null || theme == DriveTheme.DEFAULT) {
            return true;
        }
        if (theme == DriveTheme.NATURE) {
            return matchesAny(poi, NATURE_ALLOW) && !matchesAny(poi, NATURE_EXCLUDE);
        }
        if (theme == DriveTheme.CAFE) {
            return matchesAny(poi, CAFE_ALLOW) && !matchesAny(poi, CAFE_EXCLUDE);
        }
        if (theme == DriveTheme.NIGHT) {
            if (matchesAny(poi, NIGHT_ALLOW)) {
                return true;
            }
            return !matchesAny(poi, NIGHT_EXCLUDE);
        }
        return true;
    }

    public double themeScore(Poi poi, DriveTheme theme) {
        if (poi == null || theme == null || theme == DriveTheme.DEFAULT) {
            return 0.6;
        }
        if (theme == DriveTheme.NATURE) {
            return matchesAny(poi, NATURE_ALLOW) ? 1.0 : 0.2;
        }
        if (theme == DriveTheme.CAFE) {
            if (!matchesAny(poi, CAFE_ALLOW) || matchesAny(poi, CAFE_EXCLUDE)) {
                return 0.2;
            }
            return 1.0;
        }
        if (theme == DriveTheme.NIGHT) {
            if (matchesAny(poi, NIGHT_ALLOW)) {
                return 1.0;
            }
            return matchesAny(poi, NIGHT_EXCLUDE) ? 0.2 : 0.6;
        }
        return 0.6;
    }

    public List<String> matchedKeywords(Poi poi, DriveTheme theme) {
        if (poi == null || theme == null || theme == DriveTheme.DEFAULT) {
            return List.of();
        }
        Set<String> keywords = theme == DriveTheme.NATURE ? NATURE_ALLOW
            : theme == DriveTheme.CAFE ? CAFE_ALLOW
            : NIGHT_ALLOW;
        Set<String> matched = new LinkedHashSet<>();
        for (String keyword : keywords) {
            if (containsKeyword(poi, keyword)) {
                matched.add(keyword);
            }
        }
        return List.copyOf(matched);
    }

    public boolean isRelaxedAllowed(Poi poi, DriveTheme theme) {
        if (poi == null || theme == null || theme == DriveTheme.DEFAULT) {
            return true;
        }
        if (theme == DriveTheme.NATURE) {
            return !matchesAny(poi, CORE_EXCLUDE);
        }
        if (theme == DriveTheme.CAFE) {
            return !matchesAny(poi, CAFE_EXCLUDE);
        }
        if (theme == DriveTheme.NIGHT) {
            return matchesAny(poi, NIGHT_ALLOW) || !matchesAny(poi, NIGHT_EXCLUDE);
        }
        return true;
    }

    private boolean matchesAny(Poi poi, Set<String> keywords) {
        for (String keyword : keywords) {
            if (containsKeyword(poi, keyword)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsKeyword(Poi poi, String keyword) {
        String normalizedKeyword = normalize(keyword);
        if (normalizedKeyword.isBlank()) {
            return false;
        }
        String name = normalize(poi.name());
        String type = normalize(poi.type());
        if (name.contains(normalizedKeyword) || type.contains(normalizedKeyword)) {
            return true;
        }
        if (poi.tags() == null) {
            return false;
        }
        return poi.tags().stream()
            .map(this::normalize)
            .anyMatch(tag -> tag.contains(normalizedKeyword));
    }

    private String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.toLowerCase(Locale.ROOT).replace(" ", "");
    }
}
