package io.routepickapi.service.recommendation.pipeline;

import io.routepickapi.common.error.CustomException;
import io.routepickapi.common.error.ErrorType;
import io.routepickapi.domain.course.Course;
import io.routepickapi.domain.poi.Poi;
import io.routepickapi.infrastructure.client.kakao.KakaoLocalClient;
import io.routepickapi.infrastructure.client.kakao.KakaoLocalClient.Address;
import io.routepickapi.infrastructure.client.kakao.KakaoLocalClient.CoordDocument;
import io.routepickapi.infrastructure.client.kakao.KakaoLocalClient.CoordToAddressResponse;
import io.routepickapi.infrastructure.client.weather.WeatherClient;
import io.routepickapi.infrastructure.client.weather.WeatherClient.WeatherItem;
import io.routepickapi.weather.GridConverter;
import io.routepickapi.weather.WeatherBaseTimeCalculator;
import io.routepickapi.weather.WeatherBaseTimeCalculator.BaseDateTime;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationFacade {

    private static final int DEFAULT_RADIUS = 10000;
    private static final int MIN_RADIUS = 6000;
    private static final int MAX_RADIUS = 25000;
    private static final int RADIUS_PER_MINUTE = 120;
    private static final int DEFAULT_MAX_STOPS = 3;
    private static final int MAX_COURSE_LIMIT = 30;

    private final PoiCollectorService poiCollectorService;
    private final PoiNormalizationService poiNormalizationService;
    private final PoiFilterService poiFilterService;
    private final CourseGenerationService courseGenerationService;
    private final RouteCalculationService routeCalculationService;
    private final RecommendationScoringService recommendationScoringService;
    private final DriveThemePolicy driveThemePolicy;
    private final KakaoLocalClient kakaoLocalClient;
    private final WeatherClient weatherClient;
    private final WeatherBaseTimeCalculator weatherBaseTimeCalculator = new WeatherBaseTimeCalculator();
    private final GridConverter gridConverter = new GridConverter();

    public DriveCourseResult recommend(DriveCourseCommand command) {
        if (command == null) {
            throw new CustomException(ErrorType.COMMON_INVALID_INPUT, "추천 요청이 비어있습니다.");
        }

        validateCoordinates(command.originLat(), command.originLng());

        String requestId = command.requestId() == null || command.requestId().isBlank()
            ? UUID.randomUUID().toString()
            : command.requestId();
        LocalDateTime departureTime = command.departureTime() == null
            ? LocalDateTime.now()
            : command.departureTime();
        boolean weatherAware = command.weatherAware() == null || command.weatherAware();

        double centerLat = command.originLat();
        double centerLng = command.originLng();
        int radius = resolveRadius(command.durationMinutes());
        int maxStops = sanitizeMaxStops(command.maxStops());
        String theme = driveThemePolicy.resolve(command.theme());

        log.info(
            "추천 요청 시작 - requestId={}, originLat={}, originLng={}, theme={}, durationMinutes={}, maxStops={}, weatherAware={}",
            requestId,
            command.originLat(),
            command.originLng(),
            theme,
            command.durationMinutes(),
            maxStops,
            weatherAware
        );

        PoiCollectionRequest collectionRequest = new PoiCollectionRequest(
            centerLat,
            centerLng,
            radius,
            null,
            null
        );

        RawPoiBundle rawPoiBundle = poiCollectorService.collect(collectionRequest);
        Map<String, Integer> rawCounts = countRawPois(rawPoiBundle);
        log.info(
            "POI 수집 완료 - requestId={}, kakao={}, tour={}, overpass={}, total={}",
            requestId,
            rawCounts.get("kakao"),
            rawCounts.get("tour"),
            rawCounts.get("overpass"),
            rawCounts.get("total")
        );

        List<Poi> pois = poiNormalizationService.normalize(rawPoiBundle);
        log.info("POI 정규화 완료 - requestId={}, normalized={}", requestId, pois.size());

        List<Poi> filtered = poiFilterService.filter(pois, requestId);
        log.info("POI 필터링 완료 - requestId={}, filtered={}", requestId, filtered.size());

        List<CoursePlan> plans = courseGenerationService.generate(filtered, maxStops, MAX_COURSE_LIMIT);
        log.info("코스 조합 생성 완료 - requestId={}, plans={}", requestId, plans.size());

        String region = resolveRegion(command.originLng(), command.originLat());
        List<Course> courses = routeCalculationService.calculate(plans, region, theme);
        log.info("경로 계산 완료 - requestId={}, courses={}", requestId, courses.size());
        WeatherSnapshot weatherSnapshot = resolveWeatherSnapshot(
            centerLat,
            centerLng,
            departureTime,
            weatherAware
        );
        List<Course> scoredCourses = recommendationScoringService.score(courses, weatherSnapshot);
        List<Course> finalCourses = applyDurationFilter(scoredCourses, command.durationMinutes());
        int removedByDuration = scoredCourses.size() - finalCourses.size();
        if (removedByDuration > 0) {
            log.info(
                "코스 필터링 완료 - requestId={}, removedByDuration={}, remaining={}",
                requestId,
                removedByDuration,
                finalCourses.size()
            );
        }

        log.info("추천 파이프라인 완료 - requestId={}, finalCourses={}", requestId, finalCourses.size());
        return new DriveCourseResult(
            requestId,
            command.originLat(),
            command.originLng(),
            departureTime,
            finalCourses,
            LocalDateTime.now()
        );
    }

    private int sanitizeMaxStops(Integer maxStops) {
        if (maxStops == null || maxStops <= 0) {
            return DEFAULT_MAX_STOPS;
        }
        return Math.max(2, Math.min(maxStops, 4));
    }

    private int resolveRadius(Integer durationMinutes) {
        if (durationMinutes == null || durationMinutes <= 0) {
            return DEFAULT_RADIUS;
        }
        int computed = Math.max(MIN_RADIUS, durationMinutes * RADIUS_PER_MINUTE);
        return Math.min(MAX_RADIUS, computed);
    }

    private Map<String, Integer> countRawPois(RawPoiBundle rawPoiBundle) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        int kakaoCount = rawPoiBundle == null || rawPoiBundle.kakaoPlaces() == null
            ? 0
            : rawPoiBundle.kakaoPlaces().size();
        int tourCount = rawPoiBundle == null || rawPoiBundle.tourItems() == null
            ? 0
            : rawPoiBundle.tourItems().size();
        int overpassCount = rawPoiBundle == null || rawPoiBundle.overpassElements() == null
            ? 0
            : rawPoiBundle.overpassElements().size();

        counts.put("kakao", kakaoCount);
        counts.put("tour", tourCount);
        counts.put("overpass", overpassCount);
        counts.put("total", kakaoCount + tourCount + overpassCount);
        return counts;
    }

    private void validateCoordinates(double lat, double lng) {
        if (lat < -90.0 || lat > 90.0 || lng < -180.0 || lng > 180.0) {
            throw new CustomException(ErrorType.COMMON_INVALID_INPUT, "좌표가 올바르지 않습니다.");
        }
    }

    private String resolveRegion(double longitude, double latitude) {
        CoordToAddressResponse response = kakaoLocalClient.coordToAddress(longitude, latitude);
        if (response == null || response.documents() == null || response.documents().isEmpty()) {
            return "UNKNOWN";
        }

        CoordDocument document = response.documents().getFirst();
        if (document.roadAddress() != null) {
            return buildRegion(document.roadAddress().region1DepthName(), document.roadAddress().region2DepthName());
        }
        if (document.address() != null) {
            return buildRegion(document.address().region1DepthName(), document.address().region2DepthName());
        }
        return "UNKNOWN";
    }

    private String buildRegion(String region1, String region2) {
        if (region1 == null || region1.isBlank()) {
            return "UNKNOWN";
        }
        if (region2 == null || region2.isBlank()) {
            return region1;
        }
        return region1 + " " + region2;
    }

    private WeatherSnapshot resolveWeatherSnapshot(
        double centerLat,
        double centerLng,
        LocalDateTime departureTime,
        boolean weatherAware
    ) {
        if (!weatherAware) {
            return new WeatherSnapshot(75.0, null, false);
        }
        BaseDateTime baseDateTime = weatherBaseTimeCalculator.forUltraShortForecast(departureTime);
        GridConverter.GridPoint grid = gridConverter.toGrid(centerLat, centerLng);
        List<WeatherItem> items = weatherClient.fetchUltraShortForecast(
            baseDateTime.baseDate(),
            baseDateTime.baseTime(),
            grid.nx(),
            grid.ny()
        );
        return buildWeatherSnapshot(items);
    }

    private List<Course> applyDurationFilter(List<Course> courses, Integer durationMinutes) {
        if (durationMinutes == null || durationMinutes <= 0) {
            return courses;
        }

        List<Course> filtered = courses.stream()
            .filter(course -> course.totalDuration().toMinutes() <= durationMinutes)
            .toList();

        if (filtered.isEmpty()) {
            return courses;
        }

        return filtered;
    }

    private WeatherSnapshot buildWeatherSnapshot(List<WeatherItem> items) {
        if (items == null || items.isEmpty()) {
            return new WeatherSnapshot(65.0, null, false);
        }

        Double precipitationType = findWeatherValue(items, "PTY");
        Double skyState = findWeatherValue(items, "SKY");
        Double temperature = findWeatherValue(items, "T1H");
        if (temperature == null) {
            temperature = findWeatherValue(items, "TMP");
        }

        double score = 100.0;
        boolean precipitation = precipitationType != null && precipitationType > 0.0;
        if (precipitation) {
            score -= 40.0;
        }
        if (skyState != null) {
            if (skyState >= 4.0) {
                score -= 20.0;
            } else if (skyState >= 3.0) {
                score -= 10.0;
            }
        }
        if (temperature != null && (temperature <= -5.0 || temperature >= 33.0)) {
            score -= 10.0;
        }
        score = Math.max(0.0, Math.min(100.0, score));

        return new WeatherSnapshot(score, temperature, precipitation);
    }

    private Double findWeatherValue(List<WeatherItem> items, String category) {
        return items.stream()
            .filter(item -> item.category() != null && item.category().equalsIgnoreCase(category))
            .map(item -> item.fcstValue() == null || item.fcstValue().isBlank()
                ? item.obsrValue()
                : item.fcstValue())
            .filter(value -> value != null && !value.isBlank())
            .map(this::parseDouble)
            .filter(value -> value != null)
            .findFirst()
            .orElse(null);
    }

    private Double parseDouble(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException ex) {
            log.warn("날씨 값 파싱 실패 - value={}", raw);
            return null;
        }
    }
}
