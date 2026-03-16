package io.routepickapi.service.recommendation.pipeline;

import java.time.LocalDateTime;

/**
 * 드라이브 코스 추천 파이프라인 입력 Command.
 */
public record DriveCourseCommand(
    String requestId,
    double originLat,
    double originLng,
    String theme,
    Integer durationMinutes,
    Integer maxStops,
    LocalDateTime departureTime,
    Boolean weatherAware
) {
}
