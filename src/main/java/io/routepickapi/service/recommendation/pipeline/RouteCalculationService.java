package io.routepickapi.service.recommendation.pipeline;

import io.routepickapi.domain.course.Course;
import io.routepickapi.domain.course.CourseStop;
import io.routepickapi.domain.poi.Poi;
import io.routepickapi.domain.recommendation.ScoreBreakdown;
import io.routepickapi.dto.recommendation.GeoPoint;
import io.routepickapi.infrastructure.client.routing.KakaoRoutingClient;
import io.routepickapi.infrastructure.client.routing.KakaoRoutingClient.SegmentResult;
import io.routepickapi.infrastructure.client.routing.dto.Coordinate;
import io.routepickapi.service.recommendation.RecommendationCacheService;
import io.routepickapi.service.recommendation.RouteMetricsService.RouteLegMetrics;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RouteCalculationService {

    private final KakaoRoutingClient routingClient;
    private final RecommendationCacheService cacheService;

    public List<Course> calculate(
        List<CoursePlan> plans,
        GeoPoint origin,
        GeoPoint destination,
        String region,
        String theme
    ) {
        if (plans == null || plans.isEmpty()) {
            return List.of();
        }

        List<Course> courses = new ArrayList<>();
        int routingRequests = 0;
        int routingFailures = 0;
        int routingSuccess = 0;
        int fallbackCourses = 0;
        boolean fallbackOnly = false;
        for (CoursePlan plan : plans) {
            List<Poi> stops = plan.stops();
            if (stops == null || stops.size() < 2) {
                continue;
            }

            if (origin == null || destination == null) {
                Course fallback = buildFallbackCourse(stops, origin, destination, region, theme);
                if (fallback != null) {
                    courses.add(fallback);
                    fallbackCourses++;
                }
                continue;
            }

            if (fallbackOnly) {
                Course fallback = buildFallbackCourse(stops, origin, destination, region, theme);
                if (fallback != null) {
                    courses.add(fallback);
                    fallbackCourses++;
                }
                continue;
            }

            routingRequests++;
            List<CourseStop> courseStops = new ArrayList<>();
            double totalDistance = 0.0;
            Duration totalDuration = Duration.ZERO;
            boolean invalid = false;
            GeoPoint previous = origin;

            for (int index = 0; index < stops.size(); index++) {
                Poi poi = stops.get(index);
                GeoPoint current = new GeoPoint(poi.lng(), poi.lat());
                RouteLegMetrics segment = fetchSegmentMetrics(previous, current);
                if (segment == null) {
                    invalid = true;
                    break;
                }
                double segmentDistance = segment.distanceKm();
                Duration segmentDuration = toDuration(segment.durationMinutes());
                if (segmentDistance > 0 && segmentDuration.isZero()) {
                    invalid = true;
                    break;
                }
                totalDistance += segmentDistance;
                totalDuration = totalDuration.plus(segmentDuration).plus(poi.stayDuration());
                courseStops.add(new CourseStop(
                    index,
                    poi,
                    poi.stayDuration(),
                    segmentDistance,
                    segmentDuration,
                    !segment.routingSuccess()
                ));
                previous = current;
            }

            RouteLegMetrics finalSegment = invalid ? null : fetchSegmentMetrics(previous, destination);
            if (finalSegment == null) {
                invalid = true;
            } else {
                double finalLegDistance = finalSegment.distanceKm();
                Duration finalLegDuration = toDuration(finalSegment.durationMinutes());
                if (finalLegDistance > 0 && finalLegDuration.isZero()) {
                    invalid = true;
                }
                totalDistance += finalLegDistance;
                totalDuration = totalDuration.plus(finalLegDuration);
            }

            if (invalid || totalDistance <= 1.0) {
                routingFailures++;
                fallbackOnly = true;
                Course fallback = buildFallbackCourse(stops, origin, destination, region, theme);
                if (fallback != null) {
                    courses.add(fallback);
                    fallbackCourses++;
                }
                continue;
            }

            routingSuccess++;
            courses.add(new Course(
                null,
                region,
                theme,
                totalDistance,
                totalDuration,
                0.0,
                courseStops,
                new ScoreBreakdown(0, 0, 0, 0, 0, List.of()),
                LocalDateTime.now()
            ));
        }

        log.info(
            "Routing summary - requests={}, success={}, failures={}, fallbackCourses={}, fallbackOnly={}",
            routingRequests,
            routingSuccess,
            routingFailures,
            fallbackCourses,
            fallbackOnly
        );
        log.info("경로 계산 완료 - count={}", courses.size());
        return courses;
    }

    private Course buildFallbackCourse(
        List<Poi> stops,
        GeoPoint origin,
        GeoPoint destination,
        String region,
        String theme
    ) {
        if (stops == null || stops.size() < 2) {
            return null;
        }
        if (origin == null || destination == null) {
            return null;
        }

        List<CourseStop> courseStops = new ArrayList<>();
        double totalDistance = 0.0;
        Duration totalDuration = Duration.ZERO;
        GeoPoint previous = origin;

        for (int index = 0; index < stops.size(); index++) {
            Poi poi = stops.get(index);
            GeoPoint current = new GeoPoint(poi.lng(), poi.lat());
            double segmentDistance = estimateDistance(previous, current);
            Duration segmentDuration = segmentDistance <= 0
                ? Duration.ZERO
                : Duration.ofMinutes(estimateMinutes(segmentDistance));
            totalDistance += segmentDistance;
            totalDuration = totalDuration.plus(segmentDuration).plus(poi.stayDuration());
            previous = current;
            courseStops.add(new CourseStop(
                index,
                poi,
                poi.stayDuration(),
                segmentDistance,
                segmentDuration,
                true
            ));
        }

        double finalLegDistance = estimateDistance(previous, destination);
        Duration finalLegDuration = finalLegDistance <= 0
            ? Duration.ZERO
            : Duration.ofMinutes(estimateMinutes(finalLegDistance));
        totalDistance += finalLegDistance;
        totalDuration = totalDuration.plus(finalLegDuration);

        if (totalDistance <= 1.0) {
            return null;
        }

        return new Course(
            null,
            region,
            theme,
            totalDistance,
            totalDuration,
            0.0,
            courseStops,
            new ScoreBreakdown(0, 0, 0, 0, 0, List.of()),
            LocalDateTime.now()
        );
    }

    private double estimateDistance(GeoPoint from, GeoPoint to) {
        return io.routepickapi.service.recommendation.GeoUtils.distanceKm(
            from,
            to
        );
    }

    private long estimateMinutes(double distanceKm) {
        return io.routepickapi.service.recommendation.GeoUtils.estimateMinutes(distanceKm);
    }

    private RouteLegMetrics fetchSegmentMetrics(GeoPoint origin, GeoPoint destination) {
        String segmentKey = buildSegmentCacheKey(origin, destination);
        RouteLegMetrics cached = cacheService.getRouteMetrics(segmentKey);
        if (cached != null) {
            return cached;
        }
        SegmentResult result;
        try {
            result = routingClient.fetchSegmentMetrics(
                new Coordinate(origin.x(), origin.y()),
                new Coordinate(destination.x(), destination.y())
            );
        } catch (Exception ex) {
            log.debug("Routing segment fallback", ex);
            RouteLegMetrics fallback = fallbackSegmentMetrics(origin, destination, false);
            cacheService.putRouteMetrics(segmentKey, fallback);
            return null;
        }
        if (result == null) {
            return null;
        }
        if (result.isBlocked()) {
            RouteLegMetrics fallback = fallbackSegmentMetrics(origin, destination, false);
            cacheService.putRouteMetrics(segmentKey, fallback);
            return null;
        }
        RouteLegMetrics metrics;
        if (result.routingSuccess() && (result.distanceKm() > 0 || result.durationMinutes() > 0)) {
            metrics = new RouteLegMetrics(result.distanceKm(), result.durationMinutes(), true);
        } else {
            metrics = fallbackSegmentMetrics(origin, destination, false);
        }
        cacheService.putRouteMetrics(segmentKey, metrics);
        return metrics;
    }

    private RouteLegMetrics fallbackSegmentMetrics(
        GeoPoint origin,
        GeoPoint destination,
        boolean routingSuccess
    ) {
        double distanceKm = estimateDistance(origin, destination);
        return new RouteLegMetrics(distanceKm, estimateMinutes(distanceKm), routingSuccess);
    }

    private String buildSegmentCacheKey(GeoPoint origin, GeoPoint destination) {
        return new StringBuilder("route-metrics-segment:")
            .append(formatPoint(origin))
            .append(":")
            .append(formatPoint(destination))
            .toString();
    }

    private String formatPoint(GeoPoint point) {
        if (point == null) {
            return "0.00000,0.00000";
        }
        return String.format("%.5f,%.5f", point.y(), point.x());
    }

    private Duration toDuration(double minutes) {
        if (minutes <= 0) {
            return Duration.ZERO;
        }
        return Duration.ofSeconds(Math.max(0, Math.round(minutes * 60.0)));
    }
}
