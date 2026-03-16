package io.routepickapi.domain.recommendation;

import io.routepickapi.domain.course.Course;
import java.time.LocalDateTime;
import java.util.List;

public record RecommendationResult(
    String requestId,
    double originLat,
    double originLng,
    LocalDateTime departureTime,
    List<Course> courses,
    LocalDateTime generatedAt
) {

    public RecommendationResult {
        if (requestId == null || requestId.isBlank()) {
            throw new IllegalArgumentException("requestId required");
        }
        if (originLat < -90.0 || originLat > 90.0) {
            throw new IllegalArgumentException("originLat out of range");
        }
        if (originLng < -180.0 || originLng > 180.0) {
            throw new IllegalArgumentException("originLng out of range");
        }
        departureTime = departureTime == null ? LocalDateTime.now() : departureTime;
        courses = courses == null ? List.of() : List.copyOf(courses);
        generatedAt = generatedAt == null ? LocalDateTime.now() : generatedAt;
    }
}
