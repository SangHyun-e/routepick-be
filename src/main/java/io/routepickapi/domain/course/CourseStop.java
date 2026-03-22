package io.routepickapi.domain.course;

import io.routepickapi.domain.poi.Poi;
import java.time.Duration;

public record CourseStop(
    int order,
    Poi poi,
    Duration stayDuration,
    double segmentDistanceKm,
    Duration segmentDuration,
    boolean routingEstimated
) {

    public CourseStop {
        if (order < 0) {
            throw new IllegalArgumentException("order must be non-negative");
        }
        if (poi == null) {
            throw new IllegalArgumentException("poi required");
        }
        stayDuration = stayDuration == null ? Duration.ZERO : stayDuration;
        segmentDuration = segmentDuration == null ? Duration.ZERO : segmentDuration;
    }

    public CourseStop(
        int order,
        Poi poi,
        Duration stayDuration,
        double segmentDistanceKm,
        Duration segmentDuration
    ) {
        this(order, poi, stayDuration, segmentDistanceKm, segmentDuration, false);
    }
}
