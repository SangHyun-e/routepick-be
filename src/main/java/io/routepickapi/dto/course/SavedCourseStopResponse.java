package io.routepickapi.dto.course;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "저장된 경유지 정보")
public record SavedCourseStopResponse(
    @Schema(description = "정차 순서") int order,
    @Schema(description = "장소명") String name,
    @Schema(description = "위도") Double lat,
    @Schema(description = "경도") Double lng,
    @Schema(description = "유형") String type,
    @Schema(description = "태그 목록") List<String> tags,
    @Schema(description = "체류 시간(분)") Long stayMinutes,
    @Schema(description = "경관 점수") Double viewScore,
    @Schema(description = "드라이브 적합성 점수") Double driveSuitability,
    @Schema(description = "이전 구간 거리(km)") Double segmentDistanceKm,
    @Schema(description = "이전 구간 시간(분)") Long segmentDurationMinutes
) {
}
