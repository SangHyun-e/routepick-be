package io.routepickapi.service.recommendation.pipeline;

import io.routepickapi.domain.course.Course;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 드라이브 코스 추천 파이프라인 출력 Result.
 */
public record DriveCourseResult(
    String requestId,
    double originLat,
    double originLng,
    LocalDateTime departureTime,
    List<Course> courses,
    LocalDateTime generatedAt
) {
}
