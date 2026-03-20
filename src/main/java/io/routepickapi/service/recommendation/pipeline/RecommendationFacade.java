package io.routepickapi.service.recommendation.pipeline;

import io.routepickapi.common.error.CustomException;
import io.routepickapi.common.error.ErrorType;
import io.routepickapi.domain.course.Course;
import io.routepickapi.domain.course.CourseStop;
import io.routepickapi.domain.poi.Poi;
import io.routepickapi.dto.recommendation.IncludeStopRequest;
import io.routepickapi.dto.recommendation.GeoPoint;
import io.routepickapi.infrastructure.client.kakao.KakaoLocalClient;
import io.routepickapi.infrastructure.client.kakao.dto.KakaoCoordDocument;
import io.routepickapi.infrastructure.client.kakao.dto.KakaoCoordToAddressResponse;
import io.routepickapi.infrastructure.client.weather.WeatherClient;
import io.routepickapi.infrastructure.client.weather.dto.WeatherItem;
import io.routepickapi.service.recommendation.RecommendationCacheService;
import io.routepickapi.service.recommendation.RouteCorridor;
import io.routepickapi.service.recommendation.RouteMetrics;
import io.routepickapi.service.recommendation.RouteMetricsService;
import io.routepickapi.weather.GridConverter;
import io.routepickapi.weather.WeatherBaseTimeCalculator;
import io.routepickapi.weather.WeatherBaseTimeCalculator.BaseDateTime;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
    private static final int DEFAULT_COURSE_LIMIT = 3;
    private static final int DEFAULT_ROUTING_CANDIDATE_CAP = 12;
    private static final double MAX_ROUTE_DEVIATION_KM = 3.0;
    private static final double COURSE_SIMILARITY_THRESHOLD = 0.75;
    private static final double COURSE_NAME_SIMILARITY_THRESHOLD = 0.7;
    private static final List<String> KAKAO_CAFE_KEYWORDS = List.of("카페", "뷰카페");
    private static final double ORIGIN_CLUSTER_LIMIT_KM = 1.0;
    private static final double MIN_COURSE_SCORE = 55.0;
    private static final int MAX_INCLUDE_STOPS = 3;
    private static final String INCLUDE_SOURCE = "INCLUDE";
    private static final String INCLUDE_TAG = "include";
    private static final double INCLUDE_VIEW_SCORE = 0.4;
    private static final double INCLUDE_DRIVE_SCORE = 0.4;
    private static final double INCLUDE_WEATHER_SENSITIVITY = 0.2;
    private static final int INCLUDE_STAY_MINUTES = 40;

    @org.springframework.beans.factory.annotation.Value("${recommendation.cache.version:v1}")
    private String recommendationCacheVersion;

    private final PoiCollectorService poiCollectorService;
    private final PoiNormalizationService poiNormalizationService;
    private final PoiFilterService poiFilterService;
    private final CourseGenerationService courseGenerationService;
    private final RouteCalculationService routeCalculationService;
    private final RecommendationScoringService recommendationScoringService;
    private final DriveThemePolicy driveThemePolicy;
    private final DriveSpotService driveSpotService;
    private final PoiScoringService poiScoringService;
    private final PoiThemePolicy poiThemePolicy;
    private final RoutePathService routePathService;
    private final KakaoLocalClient kakaoLocalClient;
    private final WeatherClient weatherClient;
    private final RouteMetricsService routeMetricsService;
    private final RecommendationCacheService cacheService;
    private final WeatherBaseTimeCalculator weatherBaseTimeCalculator = new WeatherBaseTimeCalculator();
    private final GridConverter gridConverter = new GridConverter();

    @org.springframework.beans.factory.annotation.Value("${recommendation.cap.course-plans:3}")
    private int coursePlanCap;

    @org.springframework.beans.factory.annotation.Value("${recommendation.cap.routing:5}")
    private int routingCandidateCap;

    public DriveCourseResult recommend(DriveCourseCommand command) {
        if (command == null) {
            throw new CustomException(ErrorType.COMMON_INVALID_INPUT, "추천 요청이 비어있습니다.");
        }

        String requestId = command.requestId() == null || command.requestId().isBlank()
            ? UUID.randomUUID().toString()
            : command.requestId();
        validateCoordinates(command.originLat(), command.originLng());
        boolean destinationProvided = command.destinationLat() != null && command.destinationLng() != null;
        log.info(
            "RecommendationFacade entry - requestId={}, destinationProvided={}",
            requestId,
            destinationProvided
        );
        if (!destinationProvided) {
            log.warn("destination coordinates missing - requestId={}, originLat={}, originLng={}",
                requestId, command.originLat(), command.originLng());
            throw new CustomException(ErrorType.COMMON_INVALID_INPUT, "destination 좌표가 필요합니다.");
        }
        validateCoordinates(command.destinationLat(), command.destinationLng());
        log.info("B-structure active - requestId={}, destinationLat={}, destinationLng={}",
            requestId, command.destinationLat(), command.destinationLng());

        List<IncludeStopRequest> includeStops = normalizeIncludeStops(command.includeStops());
        String cacheKey = buildResultCacheKey(command, includeStops);
        DriveCourseResult cached = cacheService.getDriveCourseResult(cacheKey);
        if (cached != null) {
            log.info("Recommendation cache hit - requestId={}, key={}", requestId, cacheKey);
            return new DriveCourseResult(
                requestId,
                cached.originLat(),
                cached.originLng(),
                cached.departureTime(),
                cached.courses(),
                cached.recommendedStops(),
                LocalDateTime.now()
            );
        }
        log.info("Recommendation cache miss - requestId={}, key={}", requestId, cacheKey);
        if (!includeStops.isEmpty()) {
            log.info("Include stops applied - requestId={}, count={}", requestId, includeStops.size());
        }
        LocalDateTime departureTime = command.departureTime() == null
            ? LocalDateTime.now()
            : command.departureTime();
        boolean weatherAware = command.weatherAware() == null || command.weatherAware();

        int radius = resolveRadius(command.durationMinutes());
        double maxDistanceKm = resolveMaxDistanceKm(command.durationMinutes());
        int maxStops = sanitizeMaxStops(command.maxStops());
        DriveTheme themeType = DriveTheme.fromRaw(command.theme());
        String theme = driveThemePolicy.resolve(command.theme());
        GeoPoint originPoint = new GeoPoint(command.originLng(), command.originLat());
        GeoPoint destinationPoint = new GeoPoint(command.destinationLng(), command.destinationLat());
        RouteMetrics routeMetrics = routeMetricsService.buildMetrics(
            originPoint,
            destinationPoint,
            MAX_ROUTE_DEVIATION_KM,
            maxDistanceKm
        );
        RouteCorridor corridor = new RouteCorridor(originPoint, destinationPoint, routeMetrics);
        io.routepickapi.service.recommendation.RoutePath routePath = routePathService.resolvePath(
            originPoint,
            destinationPoint
        );
        DriveSpotService.DriveSpotStats driveSpotStats = driveSpotService.fetchStats("nature");
        log.info(
            "DriveSpot stats - requestId={}, total={}, active={}, activeNature={}",
            requestId,
            driveSpotStats.total(),
            driveSpotStats.active(),
            driveSpotStats.activeTheme()
        );

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
        log.info(
            "Route corridor metrics - requestId={}, baseDistanceKm={}, baseDurationMinutes={}, maxDistanceKm={}, corridorRadiusKm={}, maxDetourKm={}",
            requestId,
            routeMetrics.baseDistanceKm(),
            routeMetrics.baseDurationMinutes(),
            maxDistanceKm,
            routeMetrics.corridorRadiusKm(),
            routeMetrics.maxDetourKm()
        );

        List<Poi> curatedPois = driveSpotService.collectActiveSpots();
        int curatedTotal = curatedPois.size();
        List<Poi> curatedThemeFiltered = curatedPois.stream()
            .filter(poi -> poiThemePolicy.isAllowed(poi, themeType))
            .toList();
        log.info("Curated theme filter - requestId={}, before={}, after={}",
            requestId, curatedTotal, curatedThemeFiltered.size());
        List<Poi> curatedCorridor = filterByCorridor(
            curatedThemeFiltered,
            corridor,
            routeMetrics,
            routePath,
            requestId
        );
        log.info("Curated radius filter - requestId={}, before={}, after={}",
            requestId, curatedThemeFiltered.size(), curatedCorridor.size());
        List<Poi> curatedFiltered = poiFilterService.filter(curatedCorridor, DriveTheme.DEFAULT, requestId);
        log.info("Curated POI 준비 완료 - requestId={}, curated={}, filtered={}",
            requestId, curatedTotal, curatedFiltered.size());

        int candidateTarget = resolveCap(routingCandidateCap, DEFAULT_ROUTING_CANDIDATE_CAP);
        int remaining = Math.max(0, candidateTarget - curatedFiltered.size());
        RawPoiBundle rawPoiBundle = new RawPoiBundle(List.of(), List.of());
        if (remaining > 0) {
            PoiCollectionRequest collectionRequest = new PoiCollectionRequest(
                command.originLat(),
                command.originLng(),
                command.destinationLat(),
                command.destinationLng(),
                radius,
                KAKAO_CAFE_KEYWORDS,
                null
            );

            rawPoiBundle = poiCollectorService.collect(collectionRequest);
            Map<String, Integer> rawCounts = countRawPois(rawPoiBundle);
            log.info(
                "POI 자동 수집 완료 - requestId={}, kakao={}, tour={}, total={}",
                requestId,
                rawCounts.get("kakao"),
                rawCounts.get("tour"),
                rawCounts.get("total")
            );
        } else {
            log.info("Curated POI sufficient - requestId={}, candidateTarget={}", requestId, candidateTarget);
        }

        List<Poi> pois = poiNormalizationService.normalize(rawPoiBundle);
        if (!pois.isEmpty()) {
            log.info("POI 정규화 완료 - requestId={}, normalized={}", requestId, pois.size());
        }

        List<Poi> corridorFiltered = filterByCorridor(
            pois,
            corridor,
            routeMetrics,
            routePath,
            requestId
        );
        List<Poi> combined = new java.util.ArrayList<>();
        combined.addAll(curatedFiltered);
        combined.addAll(corridorFiltered);
        List<Poi> filtered = poiFilterService.filter(combined, themeType, requestId);
        List<Poi> originFiltered = filterOriginCluster(filtered, originPoint, requestId);
        if (originFiltered.isEmpty() && !combined.isEmpty()) {
            log.info("Candidate fallback - requestId={}, reason=empty-after-filters", requestId);
            List<Poi> relaxedTheme = poiFilterService.filterRelaxed(combined, themeType, requestId);
            List<Poi> relaxedOrigin = filterOriginCluster(relaxedTheme, originPoint, requestId);
            originFiltered = relaxedOrigin.isEmpty() ? relaxedTheme : relaxedOrigin;
        }
        List<PoiScoringService.ScoredPoi> scoredCandidates = poiScoringService.score(
            originFiltered,
            themeType,
            routeMetrics,
            routePath
        );
        List<Poi> recommendedStops = scoredCandidates.stream()
            .limit(5)
            .map(PoiScoringService.ScoredPoi::poi)
            .toList();
        List<Poi> cappedCandidates = selectBalancedCandidates(scoredCandidates, candidateTarget);
        long curatedUsed = cappedCandidates.stream()
            .filter(poi -> poi != null && "CURATED".equalsIgnoreCase(poi.source()))
            .count();
        log.info("POI 필터링 완료 - requestId={}, filtered={}, capped={}, curatedUsed={}",
            requestId, originFiltered.size(), cappedCandidates.size(), curatedUsed);

        int planLimit = resolveCap(coursePlanCap, DEFAULT_COURSE_LIMIT);
        List<Poi> includePois = buildIncludePois(includeStops);
        List<CoursePlan> plans = courseGenerationService.generate(
            cappedCandidates,
            includePois,
            maxStops,
            planLimit
        );
        log.info("코스 조합 생성 완료 - requestId={}, plans={}", requestId, plans.size());

        String region = resolveRegion(command.originLng(), command.originLat());
        List<Course> courses = routeCalculationService.calculate(plans, originPoint, destinationPoint, region, theme);
        List<Course> routingValidCourses = filterInvalidRouting(courses, requestId);
        log.info("경로 계산 완료 - requestId={}, courses={}", requestId, routingValidCourses.size());
        List<Course> scoredCourses = recommendationScoringService.score(
            routingValidCourses,
            themeType,
            routeMetrics,
            routePath,
            command.durationMinutes()
        );
        List<Course> durationFiltered = applyDurationFilter(
            scoredCourses,
            command.durationMinutes(),
            planLimit
        );
        List<Course> finalCourses = selectDiverseCourses(durationFiltered, planLimit, requestId);
        int removedByDuration = scoredCourses.size() - durationFiltered.size();
        if (removedByDuration > 0) {
            log.info(
                "코스 필터링 완료 - requestId={}, removedByDuration={}, remaining={}",
                requestId,
                removedByDuration,
                durationFiltered.size()
            );
        }
        int removedByDiversity = durationFiltered.size() - finalCourses.size();
        if (removedByDiversity > 0) {
            log.info(
                "중복/유사 코스 제거 - requestId={}, removedByDuplicate={}, remaining={}",
                requestId,
                removedByDiversity,
                finalCourses.size()
            );
        }

        List<Course> durationHardened = applyFinalDurationLimit(
            finalCourses,
            command.durationMinutes(),
            planLimit,
            requestId
        );
        int removedByFinalDuration = finalCourses.size() - durationHardened.size();
        if (removedByFinalDuration > 0) {
            log.info(
                "Final duration hardcut - requestId={}, removed={}, remaining={}",
                requestId,
                removedByFinalDuration,
                durationHardened.size()
            );
        }
        finalCourses = durationHardened;

        log.info("추천 파이프라인 완료 - requestId={}, finalCourses={}", requestId, finalCourses.size());
        DriveCourseResult result = new DriveCourseResult(
            requestId,
            command.originLat(),
            command.originLng(),
            departureTime,
            finalCourses,
            recommendedStops,
            LocalDateTime.now()
        );
        cacheResult(cacheKey, result, finalCourses, requestId);
        return result;
    }

    private void cacheResult(String cacheKey, DriveCourseResult result, List<Course> courses, String requestId) {
        if (courses == null || courses.isEmpty()) {
            log.info("Recommendation cache skip - requestId={}, reason=empty_result", requestId);
            return;
        }

        boolean fallbackOnly = isFallbackOnly(courses);
        boolean lowQuality = isLowQuality(courses);
        String reason = "normal";
        java.time.Duration ttl = cacheService.resultTtl();
        if (fallbackOnly) {
            reason = "fallback_only";
            ttl = cacheService.shortResultTtl();
        } else if (lowQuality) {
            reason = "low_quality";
            ttl = cacheService.shortResultTtl();
        }
        cacheService.putDriveCourseResult(cacheKey, result, ttl);
        log.info("Recommendation cache save - requestId={}, reason={}, ttlSeconds={}",
            requestId, reason, ttl.toSeconds());
    }

    private boolean isFallbackOnly(List<Course> courses) {
        if (courses == null || courses.isEmpty()) {
            return false;
        }
        return courses.stream().allMatch(course ->
            course != null
                && course.stops() != null
                && !course.stops().isEmpty()
                && course.stops().stream().allMatch(CourseStop::routingEstimated)
        );
    }

    private boolean isLowQuality(List<Course> courses) {
        if (courses == null || courses.isEmpty()) {
            return true;
        }
        return courses.stream()
            .mapToDouble(Course::totalScore)
            .max()
            .orElse(0.0) < MIN_COURSE_SCORE;
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

    private double resolveMaxDistanceKm(Integer durationMinutes) {
        if (durationMinutes == null || durationMinutes <= 0) {
            return 0.0;
        }
        return Math.max(10.0, durationMinutes * (2.0 / 3.0));
    }

    private Map<String, Integer> countRawPois(RawPoiBundle rawPoiBundle) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        int kakaoCount = rawPoiBundle == null || rawPoiBundle.kakaoPlaces() == null
            ? 0
            : rawPoiBundle.kakaoPlaces().size();
        int tourCount = rawPoiBundle == null || rawPoiBundle.tourItems() == null
            ? 0
            : rawPoiBundle.tourItems().size();
        counts.put("kakao", kakaoCount);
        counts.put("tour", tourCount);
        counts.put("total", kakaoCount + tourCount);
        return counts;
    }

    private List<Poi> selectBalancedCandidates(
        List<PoiScoringService.ScoredPoi> scored,
        int limit
    ) {
        if (scored == null || scored.isEmpty()) {
            return List.of();
        }
        int cap = resolveCap(limit, DEFAULT_ROUTING_CANDIDATE_CAP);
        java.util.Map<Integer, java.util.Deque<PoiScoringService.ScoredPoi>> segments = new java.util.LinkedHashMap<>();
        java.util.Comparator<PoiScoringService.ScoredPoi> comparator = (left, right) -> {
            int priority = Integer.compare(right.sourcePriority(), left.sourcePriority());
            if (priority != 0) {
                return priority;
            }
            return Double.compare(right.totalScore(), left.totalScore());
        };

        for (int segment = 0; segment < 3; segment++) {
            int segmentIndex = segment;
            List<PoiScoringService.ScoredPoi> segmentScores = scored.stream()
                .filter(item -> item.segmentIndex() == segmentIndex)
                .sorted(comparator)
                .toList();
            segments.put(segment, new java.util.ArrayDeque<>(segmentScores));
        }

        List<Poi> selected = new java.util.ArrayList<>();
        boolean added = true;
        while (selected.size() < cap && added) {
            added = false;
            for (int segment = 0; segment < 3; segment++) {
                java.util.Deque<PoiScoringService.ScoredPoi> queue = segments.get(segment);
                if (queue == null || queue.isEmpty()) {
                    continue;
                }
                if (selected.size() >= cap) {
                    break;
                }
                selected.add(queue.pollFirst().poi());
                added = true;
            }
        }

        if (selected.size() < cap) {
            List<PoiScoringService.ScoredPoi> remaining = new java.util.ArrayList<>();
            segments.values().forEach(remaining::addAll);
            remaining.stream()
                .sorted(comparator)
                .limit(cap - selected.size())
                .map(PoiScoringService.ScoredPoi::poi)
                .forEach(selected::add);
        }
        return selected;
    }

    private int resolveCap(int value, int fallback) {
        return value > 0 ? value : fallback;
    }

    private List<IncludeStopRequest> normalizeIncludeStops(List<IncludeStopRequest> includeStops) {
        if (includeStops == null || includeStops.isEmpty()) {
            return List.of();
        }
        List<IncludeStopRequest> normalized = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (IncludeStopRequest stop : includeStops) {
            if (stop == null) {
                continue;
            }
            if (stop.lat() == null || stop.lng() == null) {
                continue;
            }
            double lat = stop.lat();
            double lng = stop.lng();
            if (lat < -90.0 || lat > 90.0 || lng < -180.0 || lng > 180.0) {
                continue;
            }
            String name = stop.name() == null ? "" : stop.name().trim();
            if (name.isBlank()) {
                continue;
            }
            String key = includeStopKey(name, lat, lng);
            if (!seen.add(key)) {
                continue;
            }
            normalized.add(new IncludeStopRequest(name, lat, lng));
            if (normalized.size() >= MAX_INCLUDE_STOPS) {
                break;
            }
        }
        return normalized;
    }

    private String includeStopKey(String name, double lat, double lng) {
        String normalizedName = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        long latKey = Math.round(lat * 10000);
        long lngKey = Math.round(lng * 10000);
        return normalizedName + ":" + latKey + ":" + lngKey;
    }

    private List<Poi> buildIncludePois(List<IncludeStopRequest> includeStops) {
        if (includeStops == null || includeStops.isEmpty()) {
            return List.of();
        }
        List<Poi> includePois = new ArrayList<>();
        for (IncludeStopRequest stop : includeStops) {
            String name = stop.name().trim();
            double lat = stop.lat();
            double lng = stop.lng();
            String externalId = "include:" + includeStopKey(name, lat, lng);
            includePois.add(new Poi(
                INCLUDE_SOURCE,
                externalId,
                name,
                lat,
                lng,
                "선택 경유지",
                Set.of(INCLUDE_TAG),
                false,
                INCLUDE_VIEW_SCORE,
                INCLUDE_WEATHER_SENSITIVITY,
                Duration.ofMinutes(INCLUDE_STAY_MINUTES),
                INCLUDE_DRIVE_SCORE
            ));
        }
        return includePois;
    }

    private String buildResultCacheKey(DriveCourseCommand command, List<IncludeStopRequest> includeStops) {
        String includeKey = buildIncludeStopsKey(includeStops);
        return "drive-course:"
            + safeKey(recommendationCacheVersion)
            + ":"
            + String.format("%.5f,%.5f", command.originLat(), command.originLng())
            + ":"
            + String.format("%.5f,%.5f", command.destinationLat(), command.destinationLng())
            + ":theme=" + safeKey(command.theme())
            + ":duration=" + command.durationMinutes()
            + ":maxStops=" + command.maxStops()
            + ":weather=" + command.weatherAware()
            + ":include=" + includeKey;
    }

    private String buildIncludeStopsKey(List<IncludeStopRequest> includeStops) {
        if (includeStops == null || includeStops.isEmpty()) {
            return "none";
        }
        return includeStops.stream()
            .map(stop -> safeKey(stop.name())
                + "@"
                + String.format("%.5f,%.5f", stop.lat(), stop.lng()))
            .reduce((first, second) -> first + "|" + second)
            .orElse("none");
    }

    private String safeKey(String value) {
        if (value == null) {
            return "none";
        }
        return value.replace(" ", "");
    }

    private void validateCoordinates(double lat, double lng) {
        if (lat < -90.0 || lat > 90.0 || lng < -180.0 || lng > 180.0) {
            throw new CustomException(ErrorType.COMMON_INVALID_INPUT, "좌표가 올바르지 않습니다.");
        }
    }

    private String resolveRegion(double longitude, double latitude) {
        KakaoCoordToAddressResponse response = kakaoLocalClient.coordToAddress(longitude, latitude);
        if (response == null || response.documents() == null || response.documents().isEmpty()) {
            return "UNKNOWN";
        }

        KakaoCoordDocument document = response.documents().getFirst();
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
            return region1 + " 인근";
        }
        return region1 + " " + region2 + " 인근";
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

    private List<Course> applyDurationFilter(
        List<Course> courses,
        Integer durationMinutes,
        int targetSize
    ) {
        if (durationMinutes == null || durationMinutes <= 0) {
            return courses;
        }
        DurationConstraint constraint = DurationConstraint.from(durationMinutes);
        if (constraint == null) {
            return courses;
        }

        int minimumMinutes = Math.max(10, Math.round(durationMinutes * 0.6f));
        List<Course> withinSoft = courses.stream()
            .filter(course -> matchesDuration(course, durationMinutes, constraint.softMinutes(), minimumMinutes))
            .toList();
        if (!withinSoft.isEmpty()) {
            return withinSoft;
        }

        List<Course> withinHard = courses.stream()
            .filter(course -> matchesDuration(course, durationMinutes, constraint.hardMinutes(), minimumMinutes))
            .toList();
        if (!withinHard.isEmpty()) {
            return withinHard;
        }
        int limit = Math.min(Math.max(1, targetSize), courses.size());
        List<Course> closest = courses.stream()
            .filter(course -> course != null && course.totalDuration() != null)
            .sorted(java.util.Comparator.comparingLong(
                course -> Math.abs(course.totalDuration().toMinutes() - durationMinutes)
            ))
            .limit(limit)
            .toList();

        return closest.isEmpty() ? courses : closest;
    }

    private List<Course> applyFinalDurationLimit(
        List<Course> courses,
        Integer durationMinutes,
        int targetSize,
        String requestId
    ) {
        if (courses == null || courses.isEmpty()) {
            return List.of();
        }
        if (durationMinutes == null || durationMinutes <= 0) {
            return courses;
        }
        int extra = Math.max(40, Math.round(durationMinutes * 0.6f));
        long finalMaxMinutes = durationMinutes + extra;
        List<Course> filtered = courses.stream()
            .filter(course -> course != null && course.totalDuration() != null
                && course.totalDuration().toMinutes() <= finalMaxMinutes)
            .toList();
        int removed = courses.size() - filtered.size();
        log.info(
            "Final duration hardcut check - requestId={}, before={}, removed={}, remaining={}, finalMaxMinutes={}",
            requestId,
            courses.size(),
            removed,
            filtered.size(),
            finalMaxMinutes
        );

        if (!filtered.isEmpty()) {
            return filtered;
        }

        int fallbackExtra = Math.min(20, Math.round(durationMinutes * 0.3f));
        long fallbackMaxMinutes = finalMaxMinutes + fallbackExtra;
        List<Course> fallbackCandidates = courses.stream()
            .filter(course -> course != null && course.totalDuration() != null)
            .filter(course -> course.totalDuration().toMinutes() <= fallbackMaxMinutes)
            .toList();
        log.info(
            "Final duration fallback check - requestId={}, candidateCount={}, fallbackMaxMinutes={}",
            requestId,
            fallbackCandidates.size(),
            fallbackMaxMinutes
        );

        if (fallbackCandidates.isEmpty()) {
            log.info(
                "Final duration empty result - requestId={}, reason=no-course-within-duration-policy",
                requestId
            );
            return List.of();
        }

        List<Course> closest = fallbackCandidates.stream()
            .sorted(java.util.Comparator.comparingLong(
                course -> Math.abs(course.totalDuration().toMinutes() - durationMinutes)
            ))
            .limit(1)
            .toList();
        log.info(
            "Final duration fallback applied - requestId={}, applied={}, returnedCount={}",
            requestId,
            !closest.isEmpty(),
            closest.size()
        );
        return closest;
    }

    private List<Course> filterInvalidRouting(List<Course> courses, String requestId) {
        if (courses == null || courses.isEmpty()) {
            return List.of();
        }

        List<Course> valid = courses.stream()
            .filter(this::isValidRoutingCourse)
            .toList();

        int removed = courses.size() - valid.size();
        if (removed > 0) {
            log.info("Routing 실패 코스 제거 - requestId={}, removed={}, remaining={}",
                requestId, removed, valid.size());
        }
        return valid;
    }

    private boolean isValidRoutingCourse(Course course) {
        if (course == null || course.totalDistanceKm() <= 1.0) {
            return false;
        }
        if (course.stops() == null || course.stops().isEmpty()) {
            return false;
        }
        for (CourseStop stop : course.stops()) {
            if (stop == null) {
                return false;
            }
            if (stop.order() > 0 && stop.segmentDuration().isZero()) {
                return false;
            }
        }
        return true;
    }

    private List<Course> selectDiverseCourses(List<Course> courses, int limit, String requestId) {
        if (courses == null || courses.isEmpty()) {
            return List.of();
        }

        int cap = resolveCap(limit, DEFAULT_COURSE_LIMIT);
        List<Course> selected = new java.util.ArrayList<>();
        java.util.Set<String> firstStopKeys = new java.util.LinkedHashSet<>();
        int firstStopRemoved = 0;
        List<Course> fallbackCandidates = new java.util.ArrayList<>();
        for (Course course : courses) {
            if (course == null || course.stops() == null) {
                continue;
            }
            if (course.totalScore() < MIN_COURSE_SCORE) {
                continue;
            }
            String firstStopKey = firstStopKey(course);
            if (!firstStopKey.isBlank() && firstStopKeys.contains(firstStopKey)) {
                firstStopRemoved++;
                fallbackCandidates.add(course);
                continue;
            }
            if (isSimilarCourse(course, selected)) {
                fallbackCandidates.add(course);
                continue;
            }
            if (selected.size() < cap) {
                selected.add(course);
                if (!firstStopKey.isBlank()) {
                    firstStopKeys.add(firstStopKey);
                }
            } else {
                fallbackCandidates.add(course);
            }
        }

        int uniqueCount = selected.size();
        int fallbackAdded = 0;
        for (Course course : fallbackCandidates) {
            if (selected.size() >= cap) {
                break;
            }
            if (course == null || course.stops() == null) {
                continue;
            }
            if (course.totalScore() < MIN_COURSE_SCORE) {
                continue;
            }
            if (selected.contains(course)) {
                continue;
            }
            if (isSimilarCourse(course, selected)) {
                continue;
            }
            selected.add(course);
            fallbackAdded++;
        }

        int forcedAdded = 0;
        if (selected.size() < cap) {
            for (Course course : courses) {
                if (selected.size() >= cap) {
                    break;
                }
                if (course == null || course.stops() == null) {
                    continue;
                }
                if (selected.contains(course)) {
                    continue;
                }
                selected.add(course);
                forcedAdded++;
            }
        }

        if (firstStopRemoved > 0 || fallbackAdded > 0 || forcedAdded > 0) {
            log.info(
                "First stop diversity result - requestId={}, unique={}, fallbackAdded={}, forcedAdded={}, removed={}",
                requestId,
                uniqueCount,
                fallbackAdded,
                forcedAdded,
                firstStopRemoved
            );
        }
        if (selected.isEmpty() && !courses.isEmpty()) {
            selected.add(courses.getFirst());
        }
        return selected;
    }

    private String firstStopKey(Course course) {
        if (course == null || course.stops() == null || course.stops().isEmpty()) {
            return "";
        }
        CourseStop first = course.stops().getFirst();
        return stopKey(first);
    }

    private boolean matchesDuration(
        Course course,
        int targetMinutes,
        int rangeMinutes,
        int minimumMinutes
    ) {
        if (course == null || course.totalDuration() == null) {
            return false;
        }
        long minutes = course.totalDuration().toMinutes();
        if (minutes < minimumMinutes) {
            return false;
        }
        return Math.abs(minutes - targetMinutes) <= rangeMinutes;
    }

    private record DurationConstraint(int softMinutes, int hardMinutes) {
        private static final int MIN_SOFT_MINUTES = 20;
        private static final int MIN_HARD_MINUTES = 40;

        private static DurationConstraint from(Integer durationMinutes) {
            if (durationMinutes == null || durationMinutes <= 0) {
                return null;
            }
            return new DurationConstraint(MIN_SOFT_MINUTES, MIN_HARD_MINUTES);
        }
    }

    private List<Poi> filterOriginCluster(List<Poi> pois, GeoPoint origin, String requestId) {
        if (pois == null || pois.isEmpty()) {
            return List.of();
        }
        if (origin == null) {
            return pois;
        }
        List<Poi> filtered = new java.util.ArrayList<>();
        int removed = 0;
        for (Poi poi : pois) {
            if (poi == null) {
                continue;
            }
            GeoPoint point = new GeoPoint(poi.lng(), poi.lat());
            double distance = io.routepickapi.service.recommendation.GeoUtils.distanceKm(origin, point);
            if (distance < ORIGIN_CLUSTER_LIMIT_KM) {
                removed++;
                continue;
            }
            filtered.add(poi);
        }
        if (removed > 0) {
            log.info("Origin cluster removed - requestId={}, removed={}, remaining={}",
                requestId, removed, filtered.size());
        }
        return filtered;
    }

    private boolean isSimilarCourse(Course candidate, List<Course> selected) {
        if (candidate == null || candidate.stops() == null || selected == null) {
            return false;
        }
        java.util.Set<String> candidateStops = buildStopKeys(candidate);
        java.util.Set<String> candidateNames = buildStopNames(candidate);

        for (Course existing : selected) {
            if (existing == null || existing.stops() == null) {
                continue;
            }
            double stopOverlap = overlapRatio(candidateStops, buildStopKeys(existing));
            if (stopOverlap >= COURSE_SIMILARITY_THRESHOLD) {
                return true;
            }
            double nameOverlap = overlapRatio(candidateNames, buildStopNames(existing));
            if (nameOverlap >= COURSE_NAME_SIMILARITY_THRESHOLD) {
                return true;
            }
        }
        return false;
    }

    private java.util.Set<String> buildStopKeys(Course course) {
        java.util.Set<String> keys = new java.util.HashSet<>();
        if (course == null || course.stops() == null) {
            return keys;
        }
        for (CourseStop stop : course.stops()) {
            String key = stopKey(stop);
            if (!key.isBlank()) {
                keys.add(key);
            }
        }
        return keys;
    }

    private java.util.Set<String> buildStopNames(Course course) {
        java.util.Set<String> names = new java.util.HashSet<>();
        if (course == null || course.stops() == null) {
            return names;
        }
        for (CourseStop stop : course.stops()) {
            if (stop == null || stop.poi() == null || stop.poi().name() == null) {
                continue;
            }
            String name = stop.poi().name().trim().toLowerCase(java.util.Locale.ROOT);
            if (!name.isBlank()) {
                names.add(name);
            }
        }
        return names;
    }

    private double overlapRatio(java.util.Set<String> left, java.util.Set<String> right) {
        if (left == null || right == null || left.isEmpty() || right.isEmpty()) {
            return 0.0;
        }
        int overlap = 0;
        for (String value : left) {
            if (right.contains(value)) {
                overlap++;
            }
        }
        return overlap / (double) Math.min(left.size(), right.size());
    }

    private String stopKey(CourseStop stop) {
        if (stop == null || stop.poi() == null) {
            return "";
        }
        String name = stop.poi().name() == null ? "" : stop.poi().name().trim().toLowerCase(java.util.Locale.ROOT);
        long latKey = Math.round(stop.poi().lat() * 100000);
        long lngKey = Math.round(stop.poi().lng() * 100000);
        return name + "|" + latKey + "|" + lngKey;
    }

    private List<Poi> filterByCorridor(
        List<Poi> pois,
        RouteCorridor corridor,
        RouteMetrics routeMetrics,
        io.routepickapi.service.recommendation.RoutePath routePath,
        String requestId
    ) {
        if (pois == null || pois.isEmpty()) {
            return List.of();
        }

        List<Poi> filtered = new java.util.ArrayList<>();
        int removed = 0;
        int relaxed = 0;
        for (Poi poi : pois) {
            if (poi == null) {
                removed++;
                continue;
            }
            GeoPoint point = new GeoPoint(poi.lng(), poi.lat());
            double corridorRadiusKm = routeMetrics.corridorRadiusKm();
            double maxDetourKm = routeMetrics.maxDetourKm();
            double maxDelayMinutes = routeMetrics.maxDelayMinutes();
            if (routePath != null) {
                double progressRatio = routePath.progressRatio(point);
                if (progressRatio >= 0.2 && progressRatio <= 0.8) {
                    corridorRadiusKm *= 1.5;
                    maxDetourKm *= 1.5;
                    maxDelayMinutes *= 1.5;
                    relaxed++;
                }
            }
            double distanceToLineKm = io.routepickapi.service.recommendation.GeoUtils.distancePointToSegmentKm(
                corridor.origin(),
                corridor.destination(),
                point
            );
            if (distanceToLineKm > corridorRadiusKm) {
                removed++;
                continue;
            }
            double deviationKm = estimateDetourKm(routeMetrics, corridor.origin(), corridor.destination(), point);
            double delayMinutes = estimateDelayMinutes(routeMetrics, deviationKm);
            if (deviationKm > maxDetourKm || delayMinutes > maxDelayMinutes) {
                removed++;
                continue;
            }
            filtered.add(poi);
        }

        if (filtered.isEmpty()) {
            log.info("Route corridor fallback - requestId={}, remaining=0, fallbackToOriginal={}",
                requestId, pois.size());
            return pois;
        }

        if (removed > 0) {
            log.info("Route corridor filter - requestId={}, removed={}, remaining={}",
                requestId, removed, filtered.size());
        }
        if (relaxed > 0) {
            log.info("Route corridor relaxed - requestId={}, relaxedCount={}", requestId, relaxed);
        }
        return filtered;
    }

    private double estimateDetourKm(RouteMetrics routeMetrics, GeoPoint origin, GeoPoint destination, GeoPoint point) {
        double direct = routeMetrics.baseDistanceKm();
        double detour = io.routepickapi.service.recommendation.GeoUtils.distanceKm(origin, point)
            + io.routepickapi.service.recommendation.GeoUtils.distanceKm(point, destination) - direct;
        return Math.max(0.0, detour);
    }

    private double estimateDelayMinutes(RouteMetrics routeMetrics, double detourKm) {
        if (detourKm <= 0) {
            return 0.0;
        }
        double detourMinutes = io.routepickapi.service.recommendation.GeoUtils.estimateMinutes(detourKm);
        return Math.max(0.0, detourMinutes);
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
