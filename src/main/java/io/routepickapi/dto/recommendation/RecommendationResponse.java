package io.routepickapi.dto.recommendation;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 드라이브 코스 추천 응답 DTO.
 */
@Schema(description = "드라이브 코스 추천 응답")
public record RecommendationResponse(
    @Schema(description = "요청 식별자", example = "req-20241001-001")
    String requestId,
    @Schema(description = "출발 위도", example = "37.5665")
    double originLat,
    @Schema(description = "출발 경도", example = "126.9780")
    double originLng,
    @Schema(description = "출발 시간", example = "2024-10-01T09:30:00")
    LocalDateTime departureTime,
    @Schema(description = "추천 코스 목록")
    List<CourseSummaryResponse> courses,
    @Schema(description = "추천 경유지 목록")
    List<RecommendedStopResponse> recommendedStops,
    @Schema(description = "추천 생성 시각", example = "2024-10-01T09:29:58")
    LocalDateTime generatedAt
) {
}
