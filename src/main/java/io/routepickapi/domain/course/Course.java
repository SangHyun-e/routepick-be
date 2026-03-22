package io.routepickapi.domain.course;

import io.routepickapi.domain.recommendation.ScoreBreakdown;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

public record Course(
    Long id,
    String region,
    String theme,
    double totalDistanceKm,
    Duration totalDuration,
    double totalScore,
    List<CourseStop> stops,
    ScoreBreakdown scoreBreakdown,
    LocalDateTime createdAt
) {

    public Course {
        if (region == null || region.isBlank()) {
            throw new IllegalArgumentException("region required");
        }
        if (theme == null || theme.isBlank()) {
            throw new IllegalArgumentException("theme required");
        }
        totalDuration = totalDuration == null ? Duration.ZERO : totalDuration;
        stops = stops == null ? List.of() : List.copyOf(stops);
        createdAt = createdAt == null ? LocalDateTime.now() : createdAt;
    }
}
