package io.routepickapi.service.recommendation.pipeline;

import io.routepickapi.dto.recommendation.IncludeStopRequest;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 드라이브 코스 추천 파이프라인 입력 Command.
 */
public record DriveCourseCommand(
    String requestId,
    double originLat,
    double originLng,
    Double destinationLat,
    Double destinationLng,
    String theme,
    Integer durationMinutes,
    Integer maxStops,
    LocalDateTime departureTime,
    Boolean weatherAware,
    List<IncludeStopRequest> includeStops
) {
}
