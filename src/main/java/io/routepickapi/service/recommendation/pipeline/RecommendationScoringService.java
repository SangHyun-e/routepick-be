package io.routepickapi.service.recommendation.pipeline;

import io.routepickapi.domain.course.Course;
import io.routepickapi.domain.course.CourseStop;
import io.routepickapi.domain.poi.Poi;
import io.routepickapi.domain.recommendation.ScoreBreakdown;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class RecommendationScoringService {

    private static final double SCENERY_WEIGHT = 0.30;
    private static final double DRIVE_WEIGHT = 0.25;
    private static final double DIVERSITY_WEIGHT = 0.15;
    private static final double ROUTE_WEIGHT = 0.15;
    private static final double WEATHER_WEIGHT = 0.15;

    public List<Course> score(List<Course> courses, WeatherSnapshot weatherSnapshot) {
        if (courses == null || courses.isEmpty()) {
            return List.of();
        }

        List<Course> scored = new ArrayList<>();
        for (Course course : courses) {
            ScoreBreakdown breakdown = calculateBreakdown(course, weatherSnapshot);
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

    private ScoreBreakdown calculateBreakdown(Course course, WeatherSnapshot weatherSnapshot) {
        List<CourseStop> stops = course.stops();
        if (stops == null || stops.isEmpty()) {
            return new ScoreBreakdown(0, 0, 0, 0, 0, 0, List.of("no-stops"));
        }

        double sceneryScore = weightedScore(averageViewScore(stops), SCENERY_WEIGHT);
        double driveScore = weightedScore(averageDriveScore(stops), DRIVE_WEIGHT);
        double diversityScore = weightedScore(diversityScore(stops), DIVERSITY_WEIGHT);
        double routeScore = weightedScore(routeSmoothnessScore(stops), ROUTE_WEIGHT);
        double weatherScore = weightedScore(weatherScore(weatherSnapshot), WEATHER_WEIGHT);

        List<String> penalties = new ArrayList<>();
        double penaltyScore = calculatePenalty(course, penalties);

        return new ScoreBreakdown(
            sceneryScore,
            driveScore,
            diversityScore,
            routeScore,
            weatherScore,
            penaltyScore,
            penalties
        );
    }

    private double averageViewScore(List<CourseStop> stops) {
        return stops.stream()
            .map(CourseStop::poi)
            .mapToDouble(Poi::viewScore)
            .average()
            .orElse(0.0);
    }

    private double averageDriveScore(List<CourseStop> stops) {
        return stops.stream()
            .map(CourseStop::poi)
            .mapToDouble(Poi::driveSuitability)
            .average()
            .orElse(0.0);
    }

    private double diversityScore(List<CourseStop> stops) {
        Map<String, Long> groups = stops.stream()
            .map(CourseStop::poi)
            .map(Poi::type)
            .map(value -> value == null ? "" : value.toLowerCase(Locale.ROOT))
            .collect(Collectors.groupingBy(value -> value, Collectors.counting()));

        if (groups.isEmpty()) {
            return 0.0;
        }

        return Math.min(1.0, groups.size() / (double) stops.size());
    }

    private double routeSmoothnessScore(List<CourseStop> stops) {
        List<Double> durations = stops.stream()
            .skip(1)
            .map(CourseStop::segmentDuration)
            .map(Duration::toMinutes)
            .map(Long::doubleValue)
            .toList();

        if (durations.isEmpty()) {
            return 0.5;
        }

        double mean = durations.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        if (mean <= 0) {
            return 0.5;
        }

        double variance = durations.stream()
            .mapToDouble(value -> Math.pow(value - mean, 2))
            .average()
            .orElse(0.0);
        double stddev = Math.sqrt(variance);

        double normalized = 1.0 - Math.min(1.0, stddev / mean);
        return Math.max(0.0, normalized);
    }

    private double weatherScore(WeatherSnapshot weatherSnapshot) {
        if (weatherSnapshot == null) {
            return 0.6;
        }
        return Math.min(1.0, Math.max(0.0, weatherSnapshot.score() / 100.0));
    }

    private double weightedScore(double baseScore, double weight) {
        double normalized = Math.min(1.0, Math.max(0.0, baseScore));
        return normalized * 100.0 * weight;
    }

    private double calculatePenalty(Course course, List<String> reasons) {
        double penalty = 0.0;
        if (course.totalDuration().compareTo(Duration.ofHours(5)) > 0) {
            penalty += 10.0;
            reasons.add("too-long");
        }
        if (course.totalDistanceKm() > 200) {
            penalty += 10.0;
            reasons.add("too-far");
        }
        if (course.stops() != null && course.stops().size() > 4) {
            penalty += 5.0;
            reasons.add("too-many-stops");
        }
        return penalty;
    }
}
