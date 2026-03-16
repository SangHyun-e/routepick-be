package io.routepickapi.dto.recommendation;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 추천 코스 요약 응답 DTO.
 */
@Schema(description = "추천 코스 요약")
public record CourseSummaryResponse(
    @Schema(description = "코스 ID", example = "null")
    Long courseId,
    @Schema(description = "지역", example = "서울특별시 중구")
    String region,
    @Schema(description = "테마", example = "coastal")
    String theme,
    @Schema(description = "총 이동 거리(km)", example = "78.4")
    double totalDistanceKm,
    @Schema(description = "총 소요 시간(분)", example = "210")
    long totalDurationMinutes,
    @Schema(description = "총 점수", example = "72.0")
    double totalScore,
    @Schema(description = "점수 상세")
    ScoreBreakdownResponse scoreBreakdown,
    @Schema(description = "코스 정차 목록")
    List<CourseStopResponse> stops
) {
}
