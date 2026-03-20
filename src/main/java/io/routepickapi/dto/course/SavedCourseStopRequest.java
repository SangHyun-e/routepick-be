package io.routepickapi.dto.course;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@Schema(description = "저장할 경유지 정보")
public record SavedCourseStopRequest(
    @NotBlank @Schema(description = "장소명") String name,
    @NotNull @Schema(description = "위도") Double lat,
    @NotNull @Schema(description = "경도") Double lng,
    @NotBlank @Schema(description = "유형") String type,
    @Schema(description = "태그 목록") List<String> tags,
    @NotNull @Schema(description = "체류 시간(분)") Long stayMinutes,
    @NotNull @Schema(description = "경관 점수") Double viewScore,
    @NotNull @Schema(description = "드라이브 적합성 점수") Double driveSuitability,
    @NotNull @Schema(description = "이전 구간 거리(km)") Double segmentDistanceKm,
    @NotNull @Schema(description = "이전 구간 시간(분)") Long segmentDurationMinutes
) {
}
