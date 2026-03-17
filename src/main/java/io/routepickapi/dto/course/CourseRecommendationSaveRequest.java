package io.routepickapi.dto.course;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

@Schema(description = "추천 코스 저장 요청")
public record CourseRecommendationSaveRequest(
    @NotBlank @Schema(description = "출발지") String origin,
    @NotBlank @Schema(description = "도착지") String destination,
    @NotBlank @Schema(description = "추천 조건 요약") String theme,
    @Schema(description = "총 소요 시간(분)") Long totalDurationMinutes,
    @NotBlank @Schema(description = "경로 요약") String routeSummary,
    @NotBlank @Schema(description = "추천 설명") String explanation,
    @NotNull @Size(min = 1, max = 10) @Valid
    @Schema(description = "추천 정차 장소 목록")
    List<CourseStopRequest> stops
) {
}
