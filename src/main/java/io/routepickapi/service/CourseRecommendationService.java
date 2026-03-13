package io.routepickapi.service;

import io.routepickapi.common.error.CustomException;
import io.routepickapi.common.error.ErrorType;
import io.routepickapi.dto.course.CourseRecommendationRequest;
import io.routepickapi.dto.course.CourseRecommendationResponse;
import io.routepickapi.dto.course.CourseStopResponse;
import io.routepickapi.dto.course.CourseTheme;
import io.routepickapi.dto.place.KakaoPlaceSearchResponse;
import io.routepickapi.dto.place.KakaoPlaceSearchResponse.KakaoPlaceDocument;
import io.routepickapi.dto.recommendation.CandidatePlace;
import io.routepickapi.dto.recommendation.CourseCandidate;
import io.routepickapi.dto.recommendation.GeoPoint;
import io.routepickapi.service.recommendation.CandidatePlaceCollector;
import io.routepickapi.service.recommendation.CourseCandidateBuilder;
import io.routepickapi.service.recommendation.CourseScoreCalculator;
import io.routepickapi.service.recommendation.FinalRecommendationValidator;
import io.routepickapi.service.recommendation.PlaceDeduplicator;
import io.routepickapi.service.recommendation.RecommendationFallbackPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CourseRecommendationService {

    private static final int DEFAULT_MAX_STOPS = 3;
    private static final int MIN_STOPS = 2;
    private static final int MAX_STOPS = 4;

    private final KakaoLocalService kakaoLocalService;
    private final CandidatePlaceCollector candidatePlaceCollector;
    private final PlaceDeduplicator placeDeduplicator;
    private final CourseCandidateBuilder courseCandidateBuilder;
    private final CourseScoreCalculator courseScoreCalculator;
    private final FinalRecommendationValidator finalRecommendationValidator;
    private final RecommendationFallbackPolicy recommendationFallbackPolicy;

    public CourseRecommendationResponse recommend(CourseRecommendationRequest request) {
        if (request == null) {
            throw new CustomException(ErrorType.COMMON_INVALID_INPUT, "요청값이 비어있습니다.");
        }

        CourseTheme theme = CourseTheme.from(request.theme());
        int maxStops = sanitizeMaxStops(request.maxStops());
        double maxDetourKm = sanitizeMaxDetourKm(request.maxDetourKm());

        GeoPoint origin = resolvePoint(request.origin());
        GeoPoint destination = resolvePoint(request.destination());

        List<CandidatePlace> candidates = candidatePlaceCollector.collectCandidates(origin, destination, theme);
        List<CandidatePlace> deduplicated = placeDeduplicator.deduplicate(candidates);

        List<CourseCandidate> courses = courseCandidateBuilder.buildCourses(
            origin,
            destination,
            deduplicated,
            MIN_STOPS,
            maxStops
        );

        List<CourseCandidate> scored = courseScoreCalculator.scoreCourses(courses, origin, destination, theme);
        List<CourseCandidate> validated = finalRecommendationValidator.validateCourses(
            scored,
            origin,
            destination,
            maxDetourKm
        );

        if (validated.isEmpty()) {
            List<CourseCandidate> fallback = recommendationFallbackPolicy.fallback(
                deduplicated,
                origin,
                destination,
                MIN_STOPS
            );
            validated = courseScoreCalculator.scoreCourses(fallback, origin, destination, theme);
        }

        CourseCandidate bestCourse = validated.isEmpty() ? null : validated.getFirst();
        List<CourseStopResponse> stops = bestCourse == null
            ? List.of()
            : bestCourse.stops().stream().map(this::toStopResponse).toList();

        String routeSummary = buildRouteSummary(request.origin(), request.destination(), stops);
        String explanation = buildExplanation(theme, stops);

        log.info("추천 코스 생성 완료 - theme={}, stops={}", theme.label(), stops.size());
        return new CourseRecommendationResponse(stops, routeSummary, explanation);
    }

    public List<CourseStopResponse> recommendCandidates(
        String origin,
        String destination,
        CourseTheme theme,
        Integer maxDetourKm,
        int limit
    ) {
        if (limit <= 0) {
            return List.of();
        }

        GeoPoint originPoint = resolvePoint(origin);
        GeoPoint destinationPoint = resolvePoint(destination);
        List<CandidatePlace> candidates = candidatePlaceCollector.collectCandidates(originPoint, destinationPoint, theme);
        List<CandidatePlace> deduplicated = placeDeduplicator.deduplicate(candidates);

        return deduplicated.stream()
            .limit(limit)
            .map(this::toStopResponse)
            .toList();
    }

    private int sanitizeMaxStops(Integer maxStops) {
        if (maxStops == null) {
            return DEFAULT_MAX_STOPS;
        }
        return Math.max(MIN_STOPS, Math.min(MAX_STOPS, maxStops));
    }

    private double sanitizeMaxDetourKm(Integer maxDetourKm) {
        if (maxDetourKm == null) {
            return 15;
        }
        return Math.max(5, Math.min(50, maxDetourKm));
    }

    private GeoPoint resolvePoint(String query) {
        KakaoPlaceSearchResponse response = kakaoLocalService.searchKeyword(query, 1, 1);
        if (response == null || response.documents() == null || response.documents().isEmpty()) {
            throw new CustomException(ErrorType.COMMON_NOT_FOUND, "좌표를 찾을 수 없습니다.");
        }

        KakaoPlaceDocument document = response.documents().getFirst();
        GeoPoint point = parsePoint(document.x(), document.y());
        if (point == null) {
            throw new CustomException(ErrorType.COMMON_NOT_FOUND, "좌표를 찾을 수 없습니다.");
        }

        return point;
    }

    private GeoPoint parsePoint(String x, String y) {
        if (x == null || y == null) {
            return null;
        }

        try {
            return new GeoPoint(Double.parseDouble(x), Double.parseDouble(y));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private CourseStopResponse toStopResponse(CandidatePlace candidate) {
        return new CourseStopResponse(
            candidate.name(),
            candidate.address(),
            candidate.x(),
            candidate.y(),
            candidate.categoryName()
        );
    }

    private String buildRouteSummary(
        String origin,
        String destination,
        List<CourseStopResponse> stops
    ) {
        String stopNames = stops.stream()
            .map(CourseStopResponse::name)
            .reduce((first, second) -> first + " → " + second)
            .orElse("");

        if (stopNames.isBlank()) {
            return String.format("%s → %s", origin, destination);
        }

        return String.format("%s → %s → %s", origin, stopNames, destination);
    }

    private String buildExplanation(CourseTheme theme, List<CourseStopResponse> stops) {
        if (stops.isEmpty()) {
            return "조건에 맞는 코스가 부족해 기본 경로를 먼저 안내합니다.";
        }

        List<String> stopNames = stops.stream()
            .map(CourseStopResponse::name)
            .toList();
        String summary = String.join(", ", stopNames);
        return String.format(Locale.KOREAN,
            "%s 테마에 맞는 코스를 구성했습니다. 추천 경유지는 %s입니다.",
            theme.label(),
            summary
        );
    }
}
