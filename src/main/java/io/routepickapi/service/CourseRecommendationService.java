package io.routepickapi.service;

import io.routepickapi.common.error.CustomException;
import io.routepickapi.common.error.ErrorType;
import io.routepickapi.dto.course.CourseRecommendationRequest;
import io.routepickapi.dto.course.CourseRecommendationResponse;
import io.routepickapi.dto.course.CourseStopResponse;
import io.routepickapi.dto.course.CourseTheme;
import io.routepickapi.dto.place.KakaoPlaceSearchResponse;
import io.routepickapi.dto.place.KakaoPlaceSearchResponse.KakaoPlaceDocument;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CourseRecommendationService {

    private static final int DEFAULT_MAX_STOPS = 3;
    private static final int DEFAULT_MAX_DETOUR_KM = 10;
    private static final int MIDPOINT_COUNT = 6;
    private static final int SEARCH_RADIUS_METERS = 4000;
    private static final int SEARCH_PAGE = 1;
    private static final int SEARCH_SIZE = 15;
    private static final double EARTH_RADIUS_KM = 6371.0;

    private final KakaoLocalService kakaoLocalService;

    public CourseRecommendationResponse recommend(CourseRecommendationRequest request) {
        if (request == null) {
            throw new CustomException(ErrorType.COMMON_INVALID_INPUT, "요청값이 비어있습니다.");
        }

        CourseTheme theme = CourseTheme.from(request.theme());
        int maxStops = sanitizeMaxStops(request.maxStops());
        double maxDetourKm = sanitizeMaxDetourKm(request.maxDetourKm());

        GeoPoint origin = resolvePoint(request.origin());
        GeoPoint destination = resolvePoint(request.destination());

        List<GeoPoint> midpoints = generateMidpoints(origin, destination, MIDPOINT_COUNT);
        Map<String, Candidate> candidates = new LinkedHashMap<>();

        for (GeoPoint midpoint : midpoints) {
            for (String keyword : theme.keywords()) {
                KakaoPlaceSearchResponse response = kakaoLocalService.searchKeywordByLocation(
                    keyword,
                    midpoint.x(),
                    midpoint.y(),
                    SEARCH_RADIUS_METERS,
                    SEARCH_PAGE,
                    SEARCH_SIZE
                );

                if (response == null || response.documents() == null) {
                    continue;
                }

                for (KakaoPlaceDocument document : response.documents()) {
                    Candidate candidate = toCandidate(document, theme, origin, destination);
                    if (candidate == null || candidate.detourKm() > maxDetourKm) {
                        continue;
                    }

                    candidates.merge(candidate.key(), candidate,
                        (existing, incoming) -> incoming.score() > existing.score() ? incoming : existing);
                }
            }
        }

        List<Candidate> sorted = candidates.values().stream()
            .sorted(Comparator.comparingDouble(Candidate::score).reversed()
                .thenComparingDouble(Candidate::detourKm))
            .limit(maxStops)
            .toList();

        List<CourseStopResponse> stops = sorted.stream()
            .map(candidate -> new CourseStopResponse(
                candidate.name(),
                candidate.address(),
                candidate.point().x(),
                candidate.point().y(),
                candidate.category()
            ))
            .toList();

        String routeSummary = buildRouteSummary(request.origin(), request.destination(), stops);
        String explanation = buildExplanation(theme, stops, maxDetourKm);

        log.info("추천 코스 생성 완료 - theme={}, stops={}", theme.label(), stops.size());
        return new CourseRecommendationResponse(stops, routeSummary, explanation);
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

    private Candidate toCandidate(
        KakaoPlaceDocument document,
        CourseTheme theme,
        GeoPoint origin,
        GeoPoint destination
    ) {
        if (document == null) {
            return null;
        }

        String name = nullSafe(document.placeName());
        String category = nullSafe(document.categoryName());
        String address = resolveAddress(document);

        if (name.isBlank() || address.isBlank()) {
            return null;
        }

        if (!matchesTheme(theme, name, category, document.categoryGroupName())) {
            return null;
        }

        GeoPoint point = parsePoint(document.x(), document.y());
        if (point == null) {
            return null;
        }

        double detourKm = calculateDetourKm(origin, destination, point);
        double score = 1.0 - detourKm;

        String key = document.id() != null && !document.id().isBlank()
            ? document.id()
            : name + "|" + address;

        return new Candidate(key, name, address, category, point, score, detourKm);
    }

    private boolean matchesTheme(CourseTheme theme, String name, String category, String groupName) {
        String nameLower = name.toLowerCase(Locale.ROOT);
        String categoryLower = category.toLowerCase(Locale.ROOT);
        String groupLower = nullSafe(groupName).toLowerCase(Locale.ROOT);

        return theme.keywords().stream()
            .filter(Objects::nonNull)
            .map(keyword -> keyword.toLowerCase(Locale.ROOT))
            .anyMatch(keyword -> nameLower.contains(keyword)
                || categoryLower.contains(keyword)
                || groupLower.contains(keyword));
    }

    private String resolveAddress(KakaoPlaceDocument document) {
        if (document == null) {
            return "";
        }

        String roadAddress = nullSafe(document.roadAddressName());
        if (!roadAddress.isBlank()) {
            return roadAddress;
        }

        return nullSafe(document.addressName());
    }

    private String buildRouteSummary(String origin, String destination, List<CourseStopResponse> stops) {
        if (stops == null || stops.isEmpty()) {
            return String.format("%s에서 %s까지 추천 경유지를 찾지 못했습니다.", origin, destination);
        }

        String stopNames = stops.stream()
            .map(CourseStopResponse::name)
            .collect(Collectors.joining(" → "));

        return String.format("%s → %s → %s", origin, stopNames, destination);
    }

    private String buildExplanation(CourseTheme theme, List<CourseStopResponse> stops, double maxDetourKm) {
        List<String> lines = new ArrayList<>();
        lines.add(String.format("%s 테마에 맞춰 출발지와 도착지 사이의 중간 지점을 탐색했습니다.", theme.label()));

        if (stops == null || stops.isEmpty()) {
            lines.add("현재 조건에서 추천할 만한 장소가 부족해 기본 경로를 우선 제안합니다.");
            lines.add("테마를 변경하거나 우회 거리 조건을 완화하면 더 많은 후보를 확인할 수 있습니다.");
            return String.join("\n", lines);
        }

        String stopNames = stops.stream()
            .map(CourseStopResponse::name)
            .collect(Collectors.joining(", "));

        lines.add(String.format("추천 경유지는 %s이며 경로 이탈을 최소화했습니다.", stopNames));
        lines.add(String.format("우회 거리는 %.1fkm 이내를 기준으로 선별했습니다.", maxDetourKm));
        lines.add("주행 흐름과 휴식 타이밍을 고려해 자연스럽게 이어지도록 구성했습니다.");
        return String.join("\n", lines);
    }

    private double calculateDetourKm(GeoPoint origin, GeoPoint destination, GeoPoint stop) {
        double direct = distanceKm(origin, destination);
        double viaStop = distanceKm(origin, stop) + distanceKm(stop, destination);
        return Math.max(0, viaStop - direct);
    }

    private double distanceKm(GeoPoint start, GeoPoint end) {
        double lat1 = Math.toRadians(start.y());
        double lat2 = Math.toRadians(end.y());
        double deltaLat = Math.toRadians(end.y() - start.y());
        double deltaLon = Math.toRadians(end.x() - start.x());

        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
            + Math.cos(lat1) * Math.cos(lat2) * Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
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

    private int sanitizeMaxStops(Integer maxStops) {
        int requested = maxStops == null ? DEFAULT_MAX_STOPS : maxStops;
        return Math.min(DEFAULT_MAX_STOPS, Math.max(1, requested));
    }

    private double sanitizeMaxDetourKm(Integer maxDetourKm) {
        int requested = maxDetourKm == null ? DEFAULT_MAX_DETOUR_KM : maxDetourKm;
        return Math.max(1, requested);
    }

    private String nullSafe(String value) {
        return value == null ? "" : value.trim();
    }

    private record GeoPoint(double x, double y) {
    }

    private record Candidate(
        String key,
        String name,
        String address,
        String category,
        GeoPoint point,
        double score,
        double detourKm
    ) {
    }
}
