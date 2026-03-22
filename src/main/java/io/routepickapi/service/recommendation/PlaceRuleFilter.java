package io.routepickapi.service.recommendation;

import io.routepickapi.dto.recommendation.CandidatePlace;
import io.routepickapi.dto.recommendation.CandidateSource;
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
        CandidateSource source = place.source() == null ? CandidateSource.TOURAPI : place.source();
        List<String> keywordHits = matchKeywords(concat(place), KEYWORD_BLACKLIST);
        List<String> ruleFailures = new ArrayList<>();
        List<String> allowMatches = List.of();
        List<String> blacklistMatches = List.of();

        if (!keywordHits.isEmpty()) {
            ruleFailures.add("keyword_blacklist");
        }

        if (source == CandidateSource.KAKAO) {
            CategoryDecision categoryDecision = placeCategoryClassifier.classify(
                place.categoryGroupCode(),
                place.categoryName()
            );
            allowMatches = categoryDecision.allowlistMatches();
            blacklistMatches = categoryDecision.blacklistMatches();
            if (!categoryDecision.allowed()) {
                ruleFailures.add("category_not_allowed");
            }
            if (categoryDecision.allowedByGroup() && categoryDecision.allowlistMatches().isEmpty()) {
                ruleFailures.add("allowlist_keyword_missing");
            }
            if (!matchesCafeOrFood(place)) {
                ruleFailures.add("kakao_not_cafe_food");
            }
        }

        if (source == CandidateSource.OVERPASS && !matchesScenicTags(place)) {
            ruleFailures.add("overpass_not_scenic");
        }

        if (source == CandidateSource.TOURAPI
            && !matchesDriveSuitability(place)
            && !matchesScenicTags(place)) {
            ruleFailures.add("tourapi_low_value");
        }

        boolean passed = ruleFailures.isEmpty() && blacklistMatches.isEmpty();
        return new FilterDecision(
            passed,
            allowMatches,
            blacklistMatches,
            keywordHits,
            ruleFailures
        );
    }

    private String concat(CandidatePlace place) {
        return String.join(" ",
            safeLower(place.name()),
            safeLower(place.address()),
            safeLower(place.categoryName()),
            safeLower(place.categoryGroupName()),
            safeLower(String.join(" ", place.safeTags()))
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

    private boolean matchesCafeOrFood(CandidatePlace place) {
        String value = concat(place);
        return value.contains("카페") || value.contains("커피") || value.contains("맛집")
            || value.contains("식당") || value.contains("레스토랑") || value.contains("음식")
            || value.contains("브런치") || value.contains("베이커리");
    }

    private boolean matchesScenicTags(CandidatePlace place) {
        String value = concat(place);
        return value.contains("전망") || value.contains("viewpoint")
            || value.contains("해변") || value.contains("해안") || value.contains("coast")
            || value.contains("산") || value.contains("peak") || value.contains("자연")
            || value.contains("natural") || value.contains("scenic") || value.contains("lookout");
    }
}
