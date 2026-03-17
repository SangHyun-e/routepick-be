package io.routepickapi.service.recommendation.pipeline;

import io.routepickapi.domain.course.Course;
import io.routepickapi.domain.course.CourseStop;
import io.routepickapi.domain.recommendation.ScoreBreakdown;
import io.routepickapi.service.recommendation.RouteMetrics;
import io.routepickapi.service.recommendation.RoutePath;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RecommendationScoringService {

    private static final double THEME_WEIGHT = 0.4;
    private static final double DISTANCE_WEIGHT = 0.25;
    private static final double PROGRESS_WEIGHT = 0.2;
    private static final double REVIEW_WEIGHT = 0.15;
    private static final double FALLBACK_PENALTY = 8.0;

    private final PoiScoringService poiScoringService;

    public List<Course> score(
        List<Course> courses,
        DriveTheme theme,
        RouteMetrics routeMetrics,
        RoutePath routePath
    ) {
        if (courses == null || courses.isEmpty()) {
            return List.of();
        }

        List<Course> scored = new ArrayList<>();
        for (Course course : courses) {
            ScoreBreakdown breakdown = calculateBreakdown(course, theme, routeMetrics, routePath);
            scored.add(new Course(
                course.id(),
                course.region(),
                course.theme(),
                course.totalDistanceKm(),
                course.totalDuration(),
                breakdown.totalScore(),
                course.stops(),
                breakdown,
                course.createdAt()
            ));
        }

        return scored.stream()
            .sorted((left, right) -> Double.compare(right.totalScore(), left.totalScore()))
            .toList();
    }

    private ScoreBreakdown calculateBreakdown(
        Course course,
        DriveTheme theme,
        RouteMetrics routeMetrics,
        RoutePath routePath
    ) {
        List<CourseStop> stops = course.stops();
        if (stops == null || stops.isEmpty()) {
            return new ScoreBreakdown(0, 0, 0, 0, 0, List.of("no-stops"));
        }

        List<PoiScoringService.ScoredPoi> scoredStops = stops.stream()
            .map(stop -> poiScoringService.scoreOne(stop.poi(), theme, routeMetrics, routePath))
            .toList();

        double avgTheme = average(scoredStops.stream().mapToDouble(PoiScoringService.ScoredPoi::themeScore).toArray());
        double avgDistance = average(scoredStops.stream().mapToDouble(PoiScoringService.ScoredPoi::distanceScore).toArray());
        double avgProgress = average(scoredStops.stream().mapToDouble(PoiScoringService.ScoredPoi::progressScore).toArray());
        double avgReview = average(scoredStops.stream().mapToDouble(PoiScoringService.ScoredPoi::reviewScore).toArray());

        double themeScore = weightedScore(avgTheme, THEME_WEIGHT);
        double distanceScore = weightedScore(avgDistance, DISTANCE_WEIGHT);
        double progressScore = weightedScore(avgProgress, PROGRESS_WEIGHT);
        double reviewScore = weightedScore(avgReview, REVIEW_WEIGHT);

        List<String> penalties = new ArrayList<>();
        double penaltyScore = calculatePenalty(course, penalties);

        return new ScoreBreakdown(
            themeScore,
            distanceScore,
            progressScore,
            reviewScore,
            penaltyScore,
            penalties
        );
    }

    private double weightedScore(double baseScore, double weight) {
        double normalized = Math.min(1.0, Math.max(0.0, baseScore));
        return normalized * 100.0 * weight;
    }

    private double calculatePenalty(Course course, List<String> reasons) {
        double penalty = 0.0;
        if (isFallbackCourse(course)) {
            penalty += FALLBACK_PENALTY;
            reasons.add("routing_fallback");
        }
        return penalty;
    }

    private boolean isFallbackCourse(Course course) {
        return course.stops() != null && course.stops().stream().anyMatch(CourseStop::routingEstimated);
    }

    private double average(double[] values) {
        if (values == null || values.length == 0) {
            return 0.0;
        }
        double sum = 0.0;
        for (double value : values) {
            sum += value;
        }
        return sum / values.length;
    }
}
