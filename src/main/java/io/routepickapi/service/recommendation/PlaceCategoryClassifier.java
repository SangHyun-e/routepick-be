package io.routepickapi.service.recommendation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class PlaceCategoryClassifier {

    private static final Set<String> ALLOWED_GROUP_CODES = Set.of(
        "AT4",
        "CE7",
        "CT1"
    );

    private static final List<String> ALLOWED_CATEGORY_KEYWORDS = List.of(
        "카페",
        "공원",
        "전망대",
        "관광",
        "관광지",
        "명소",
        "드라이브",
        "해변",
        "해안",
        "호수",
        "산책",
        "휴게소",
        "야경",
        "강변",
        "자연",
        "수목원",
        "계곡",
        "국립공원",
        "산",
        "고개",
        "테마공원",
        "전시공원",
        "전망"
    );

    private static final List<String> BLACKLIST_KEYWORDS = List.of(
        "도서관",
        "병원",
        "약국",
        "학원",
        "학교",
        "종교",
        "교회",
        "성당",
        "사찰",
        "장례",
        "장례식장",
        "부동산",
        "공장",
        "산업",
        "관공서",
        "은행",
        "보험",
        "세무",
        "경찰",
        "소방",
        "숙박",
        "모텔",
        "호텔",
        "게스트하우스",
        "고시원",
        "오피스텔",
        "사무",
        "사무실",
        "빌딩",
        "창고",
        "정비",
        "물류",
        "행정",
        "주민센터",
        "청사",
        "법원"
    );

    public CategoryDecision classify(String categoryGroupCode, String categoryName) {
        String normalizedCategory = normalize(categoryName);
        boolean allowedByGroup = categoryGroupCode != null && ALLOWED_GROUP_CODES.contains(categoryGroupCode);
        List<String> allowMatches = matchKeywords(normalizedCategory, ALLOWED_CATEGORY_KEYWORDS);
        List<String> blacklistMatches = matchKeywords(normalizedCategory, BLACKLIST_KEYWORDS);
        boolean blacklisted = !blacklistMatches.isEmpty();
        boolean allowed = !blacklisted && (allowedByGroup || !allowMatches.isEmpty());

        return new CategoryDecision(allowed, allowedByGroup, allowMatches, blacklistMatches);
    }

    private String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.toLowerCase(Locale.ROOT);
    }

    private List<String> matchKeywords(String value, List<String> keywords) {
        List<String> matches = new ArrayList<>();
        if (value == null || value.isBlank()) {
            return matches;
        }
        for (String keyword : keywords) {
            if (value.contains(keyword)) {
                matches.add(keyword);
            }
        }
        return matches;
    }

    public record CategoryDecision(
        boolean allowed,
        boolean allowedByGroup,
        List<String> allowlistMatches,
        List<String> blacklistMatches
    ) {
    }
}
