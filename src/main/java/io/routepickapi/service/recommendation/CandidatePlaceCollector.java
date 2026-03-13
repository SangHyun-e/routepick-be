package io.routepickapi.service.recommendation;

import io.routepickapi.dto.course.CourseTheme;
import io.routepickapi.dto.place.KakaoPlaceSearchResponse;
import io.routepickapi.dto.place.KakaoPlaceSearchResponse.KakaoPlaceDocument;
import io.routepickapi.dto.recommendation.CandidatePlace;
import io.routepickapi.dto.recommendation.FilterDecision;
import io.routepickapi.dto.recommendation.GeoPoint;
import io.routepickapi.service.KakaoLocalService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CandidatePlaceCollector {

    private static final int MIDPOINT_COUNT = 6;
    private static final int SEARCH_RADIUS_METERS = 4000;
    private static final int SEARCH_PAGE = 1;
    private static final int SEARCH_SIZE = 15;

    private static final List<String> BASE_KEYWORDS = List.of(
        "전망대",
        "공원",
        "해변",
        "해안",
        "드라이브",
        "휴게소",
        "산책로",
        "호수",
        "야경",
        "카페",
        "관광지",
        "자연"
    );

    private final KakaoLocalService kakaoLocalService;
    private final KakaoPlaceNormalizer kakaoPlaceNormalizer;
    private final PlaceRuleFilter placeRuleFilter;

    public List<CandidatePlace> collectCandidates(
        GeoPoint origin,
        GeoPoint destination,
        CourseTheme theme
    ) {
        List<GeoPoint> searchPoints = buildSearchPoints(origin, destination);
        Set<String> keywords = buildKeywords(theme);
        Map<String, CandidatePlace> results = new LinkedHashMap<>();

        for (GeoPoint point : searchPoints) {
            for (String keyword : keywords) {
                KakaoPlaceSearchResponse response = kakaoLocalService.searchKeywordByLocation(
                    keyword,
                    point.x(),
                    point.y(),
                    SEARCH_RADIUS_METERS,
                    SEARCH_PAGE,
                    SEARCH_SIZE
                );

                if (response == null || response.documents() == null) {
                    continue;
                }

                for (KakaoPlaceDocument document : response.documents()) {
                    CandidatePlace candidate = kakaoPlaceNormalizer.normalize(document);
                    if (candidate == null) {
                        continue;
                    }

                    FilterDecision decision = placeRuleFilter.filter(candidate);
                    if (!decision.passed()) {
                        continue;
                    }

                    results.putIfAbsent(candidate.key(), candidate);
                }
            }
        }

        List<CandidatePlace> candidates = new ArrayList<>(results.values());
        log.info("후보 장소 수집 완료 - count={}", candidates.size());
        return candidates;
    }

    private List<GeoPoint> buildSearchPoints(GeoPoint origin, GeoPoint destination) {
        List<GeoPoint> points = new ArrayList<>();
        points.add(origin);
        points.add(destination);
        points.addAll(generateMidpoints(origin, destination, MIDPOINT_COUNT));
        return points;
    }

    private List<GeoPoint> generateMidpoints(GeoPoint origin, GeoPoint destination, int count) {
        List<GeoPoint> points = new ArrayList<>();
        if (count <= 0) {
            return points;
        }

        for (int index = 1; index <= count; index++) {
            double ratio = index / (double) (count + 1);
            double x = origin.x() + (destination.x() - origin.x()) * ratio;
            double y = origin.y() + (destination.y() - origin.y()) * ratio;
            points.add(new GeoPoint(x, y));
        }

        return points;
    }

    private Set<String> buildKeywords(CourseTheme theme) {
        Set<String> keywords = new LinkedHashSet<>(BASE_KEYWORDS);
        if (theme != null && theme.keywords() != null) {
            theme.keywords().stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .forEach(keywords::add);
        }
        return keywords;
    }
}
