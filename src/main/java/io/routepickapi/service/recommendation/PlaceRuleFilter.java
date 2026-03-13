package io.routepickapi.service.recommendation;

import io.routepickapi.dto.recommendation.CandidatePlace;
import io.routepickapi.dto.recommendation.FilterDecision;
import io.routepickapi.service.recommendation.PlaceCategoryClassifier.CategoryDecision;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PlaceRuleFilter {

    private static final List<String> KEYWORD_BLACKLIST = List.of(
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
        "부동산",
        "공장",
        "산업단지",
        "법원",
        "세무",
        "행정",
        "주민센터",
        "청사",
        "숙박",
        "모텔",
        "호텔",
        "게스트하우스"
    );

    private static final List<String> DRIVE_SUITABLE_KEYWORDS = List.of(
        "카페",
        "공원",
        "전망대",
        "관광",
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
        "전망"
    );

    private final PlaceCategoryClassifier placeCategoryClassifier;

    public FilterDecision filter(CandidatePlace place) {
        if (place == null) {
            return new FilterDecision(false, List.of(), List.of(), List.of(), List.of("empty_place"));
        }

        CategoryDecision categoryDecision = placeCategoryClassifier.classify(
            place.categoryGroupCode(),
            place.categoryName()
        );

        List<String> keywordHits = matchKeywords(concat(place), KEYWORD_BLACKLIST);
        List<String> ruleFailures = new ArrayList<>();

        if (!categoryDecision.allowed()) {
            ruleFailures.add("category_not_allowed");
        }

        if (categoryDecision.allowedByGroup() && categoryDecision.allowlistMatches().isEmpty()) {
            ruleFailures.add("allowlist_keyword_missing");
        }

        if (!keywordHits.isEmpty()) {
            ruleFailures.add("keyword_blacklist");
        }

        if (!matchesDriveSuitability(place)) {
            ruleFailures.add("drive_suitability");
        }

        boolean passed = ruleFailures.isEmpty() && categoryDecision.blacklistMatches().isEmpty();
        return new FilterDecision(
            passed,
            categoryDecision.allowlistMatches(),
            categoryDecision.blacklistMatches(),
            keywordHits,
            ruleFailures
        );
    }

    private String concat(CandidatePlace place) {
        return String.join(" ",
            safeLower(place.name()),
            safeLower(place.address()),
            safeLower(place.categoryName()),
            safeLower(place.categoryGroupName())
        );
    }

    private String safeLower(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT);
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

    private boolean matchesDriveSuitability(CandidatePlace place) {
        String value = concat(place);
        for (String keyword : DRIVE_SUITABLE_KEYWORDS) {
            if (value.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
