package io.routepickapi.service.recommendation.pipeline;

import io.routepickapi.domain.course.Course;
import io.routepickapi.domain.course.CourseStop;
import io.routepickapi.domain.poi.Poi;
import io.routepickapi.domain.recommendation.ScoreBreakdown;
import io.routepickapi.infrastructure.client.routing.RoutingClient;
import io.routepickapi.infrastructure.client.routing.RoutingClient.MatrixResponse;
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

    public List<Course> calculate(List<CoursePlan> plans, String region, String theme) {
        if (plans == null || plans.isEmpty()) {
            return List.of();
        }

        List<Course> courses = new ArrayList<>();
        for (CoursePlan plan : plans) {
            List<Poi> stops = plan.stops();
            if (stops == null || stops.size() < 2) {
                continue;
            }

            List<RoutingClient.Coordinate> coordinates = stops.stream()
                .map(stop -> new RoutingClient.Coordinate(stop.lng(), stop.lat()))
                .toList();

            MatrixResponse matrix = routingClient.fetchMatrix(coordinates);
            List<CourseStop> courseStops = new ArrayList<>();
            double totalDistance = 0.0;
            Duration totalDuration = Duration.ZERO;

            for (int index = 0; index < stops.size(); index++) {
                Poi poi = stops.get(index);
                double segmentDistance = resolveSegmentDistance(matrix, index);
                Duration segmentDuration = resolveSegmentDuration(matrix, index);
                totalDistance += segmentDistance;
                totalDuration = totalDuration.plus(segmentDuration).plus(poi.stayDuration());
                courseStops.add(new CourseStop(
                    index,
                    poi,
                    poi.stayDuration(),
                    segmentDistance,
                    segmentDuration
                ));
            }

            courses.add(new Course(
                null,
                region,
                theme,
                totalDistance,
                totalDuration,
                0.0,
                courseStops,
                new ScoreBreakdown(0, 0, 0, 0, 0, 0, List.of()),
                LocalDateTime.now()
            ));
        }

        log.info("경로 계산 완료 - count={}", courses.size());
        return courses;
    }

    private double resolveSegmentDistance(MatrixResponse matrix, int index) {
        if (matrix == null || matrix.distances() == null || index == 0) {
            return 0.0;
        }
        return safeMatrixValue(matrix.distances(), index - 1, index);
    }

    private Duration resolveSegmentDuration(MatrixResponse matrix, int index) {
        if (matrix == null || matrix.durations() == null || index == 0) {
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
