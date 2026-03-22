package io.routepickapi.service.recommendation.pipeline;

import io.routepickapi.domain.course.Course;
import io.routepickapi.domain.course.CourseStop;
import io.routepickapi.domain.poi.Poi;
import io.routepickapi.domain.recommendation.ScoreBreakdown;
import io.routepickapi.dto.recommendation.GeoPoint;
import io.routepickapi.infrastructure.client.routing.RoutingClient;
import io.routepickapi.infrastructure.client.routing.dto.Coordinate;
import io.routepickapi.infrastructure.client.routing.dto.MatrixResponse;
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

    private final RoutingClient routingClient;

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

            List<Coordinate> coordinates = new ArrayList<>();
            coordinates.add(new Coordinate(origin.x(), origin.y()));
            for (Poi stop : stops) {
                coordinates.add(new Coordinate(stop.lng(), stop.lat()));
            }
            coordinates.add(new Coordinate(destination.x(), destination.y()));

            if (fallbackOnly) {
                Course fallback = buildFallbackCourse(stops, origin, destination, region, theme);
                if (fallback != null) {
                    courses.add(fallback);
                    fallbackCourses++;
                }
                continue;
            }

            routingRequests++;
            MatrixResponse matrix = routingClient.fetchMatrix(coordinates);
            if (matrix == null || matrix.distances() == null || matrix.durations() == null
                || matrix.distances().isEmpty() || matrix.durations().isEmpty()) {
                routingFailures++;
                fallbackOnly = true;
                Course fallback = buildFallbackCourse(stops, origin, destination, region, theme);
                if (fallback != null) {
                    courses.add(fallback);
                    fallbackCourses++;
                }
                continue;
            }
            List<CourseStop> courseStops = new ArrayList<>();
            double totalDistance = 0.0;
            Duration totalDuration = Duration.ZERO;
            boolean invalid = false;

            for (int index = 0; index < stops.size(); index++) {
                Poi poi = stops.get(index);
                double segmentDistance = resolveSegmentDistance(matrix, index + 1);
                Duration segmentDuration = resolveSegmentDuration(matrix, index + 1);
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
                    false
                ));
            }

            double finalLegDistance = resolveSegmentDistance(matrix, stops.size() + 1);
            Duration finalLegDuration = resolveSegmentDuration(matrix, stops.size() + 1);
            if (finalLegDistance > 0 && finalLegDuration.isZero()) {
                invalid = true;
            }
            totalDistance += finalLegDistance;
            totalDuration = totalDuration.plus(finalLegDuration);

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
            "Routing matrix summary - requests={}, success={}, failures={}, fallbackCourses={}, fallbackOnly={}",
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

    private double resolveSegmentDistance(MatrixResponse matrix, int index) {
        if (matrix == null || matrix.distances() == null || index <= 0) {
            return 0.0;
        }
        return safeMatrixValue(matrix.distances(), index - 1, index);
    }

    private Duration resolveSegmentDuration(MatrixResponse matrix, int index) {
        if (matrix == null || matrix.durations() == null || index <= 0) {
            return Duration.ZERO;
        }
        double seconds = safeMatrixValue(matrix.durations(), index - 1, index);
        return Duration.ofSeconds(Math.max(0, Math.round(seconds)));
    }

    private double safeMatrixValue(List<List<Double>> matrix, int from, int to) {
        if (from < 0 || to < 0 || from >= matrix.size()) {
            return 0.0;
        }
        List<Double> row = matrix.get(from);
        if (row == null || to >= row.size() || row.get(to) == null) {
            return 0.0;
        }
        return row.get(to);
    }
}
