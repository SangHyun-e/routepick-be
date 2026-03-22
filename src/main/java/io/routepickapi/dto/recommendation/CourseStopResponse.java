package io.routepickapi.dto.recommendation;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 코스 경유지 응답 DTO.
 */
@Schema(description = "코스 정차 정보")
public record CourseStopResponse(
    @Schema(description = "정차 순서", example = "1")
    int order,
    @Schema(description = "장소명", example = "북한산 전망대")
    String name,
    @Schema(description = "위도", example = "37.658")
    double lat,
    @Schema(description = "경도", example = "126.974")
    double lng,
    @Schema(description = "유형", example = "전망대")
    String type,
    @Schema(description = "태그 목록", example = "[\"전망대\", \"mountain\", \"osm\"]")
    List<String> tags,
    @Schema(description = "체류 시간(분)", example = "60")
    long stayMinutes,
    @Schema(description = "경관 점수", example = "0.9")
    double viewScore,
    @Schema(description = "드라이브 적합성 점수", example = "0.7")
    double driveSuitability,
    @Schema(description = "이전 구간 거리(km)", example = "18.5")
    double segmentDistanceKm,
    @Schema(description = "이전 구간 시간(분)", example = "35")
    long segmentDurationMinutes
) {
}
