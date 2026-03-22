package io.routepickapi.service.recommendation.pipeline;

import io.routepickapi.domain.course.Course;
import io.routepickapi.domain.course.CourseStop;
import io.routepickapi.domain.recommendation.ScoreBreakdown;
import io.routepickapi.dto.recommendation.GeoPoint;
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
    private static final double MAX_DURATION_PENALTY = 20.0;
    private static final int MIN_DURATION_SOFT_MINUTES = 20;
    private static final int MIN_DURATION_HARD_MINUTES = 40;
    private static final double MAX_MIDPOINT_BOOST = 8.0;

    private final PoiScoringService poiScoringService;

    public List<Course> score(
        List<Course> courses,
        DriveTheme theme,
        RouteMetrics routeMetrics,
        RoutePath routePath,
        Integer durationMinutes
    ) {
        if (courses == null || courses.isEmpty()) {
            return List.of();
        }

        List<Course> scored = new ArrayList<>();
        for (Course course : courses) {
            ScoreBreakdown breakdown = calculateBreakdown(course, theme, routeMetrics, routePath, durationMinutes);
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
        RoutePath routePath,
        Integer durationMinutes
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
        double progressScore = weightedScore(avgProgress, PROGRESS_WEIGHT)
            + calculateMidpointBoost(scoredStops, routePath);
        double reviewScore = weightedScore(avgReview, REVIEW_WEIGHT);

        List<String> penalties = new ArrayList<>();
        double penaltyScore = calculatePenalty(course, penalties, durationMinutes);

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

    private double calculatePenalty(Course course, List<String> reasons, Integer durationMinutes) {
        double penalty = 0.0;
        if (isFallbackCourse(course)) {
            penalty += FALLBACK_PENALTY;
            reasons.add("routing_fallback");
        }
        penalty += calculateDurationPenalty(course, durationMinutes, reasons);
        return penalty;
    }

    private boolean isFallbackCourse(Course course) {
        return course.stops() != null && course.stops().stream().anyMatch(CourseStop::routingEstimated);
    }

    private double calculateDurationPenalty(
        Course course,
        Integer durationMinutes,
        List<String> reasons
    ) {
        if (durationMinutes == null || durationMinutes <= 0 || course == null || course.totalDuration() == null) {
            return 0.0;
        }
        long courseMinutes = course.totalDuration().toMinutes();
        long diff = Math.abs(courseMinutes - durationMinutes);
        DurationConstraint constraint = DurationConstraint.from(durationMinutes);
        if (constraint == null || diff <= constraint.softMinutes()) {
            return 0.0;
        }
        double ratio = constraint.hardMinutes() <= constraint.softMinutes()
            ? 1.0
            : Math.min(1.0, (diff - constraint.softMinutes())
                / (double) (constraint.hardMinutes() - constraint.softMinutes()));
        double penalty = MAX_DURATION_PENALTY * ratio;
        if (diff >= constraint.hardMinutes()) {
            reasons.add("duration_out_of_range");
        } else {
            reasons.add("duration_mismatch");
        }
        return penalty;
    }

    private double calculateMidpointBoost(
        List<PoiScoringService.ScoredPoi> scoredStops,
        RoutePath routePath
    ) {
        if (routePath == null || scoredStops == null || scoredStops.isEmpty()) {
            return 0.0;
        }
        int count = 0;
        for (PoiScoringService.ScoredPoi scored : scoredStops) {
            if (scored == null || scored.poi() == null) {
                continue;
            }
            GeoPoint point = new GeoPoint(scored.poi().lng(), scored.poi().lat());
            double progress = routePath.progressRatio(point);
            if (progress >= 0.2 && progress <= 0.8) {
                count++;
            }
        }
        if (count == 0) {
            return 0.0;
        }
        double ratio = count / (double) scoredStops.size();
        return MAX_MIDPOINT_BOOST * ratio;
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

    private record DurationConstraint(int softMinutes, int hardMinutes) {
        private static DurationConstraint from(Integer durationMinutes) {
            if (durationMinutes == null || durationMinutes <= 0) {
                return null;
            }
            return new DurationConstraint(MIN_DURATION_SOFT_MINUTES, MIN_DURATION_HARD_MINUTES);
        }
    }
}
