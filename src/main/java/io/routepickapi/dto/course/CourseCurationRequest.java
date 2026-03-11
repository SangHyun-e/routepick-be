package io.routepickapi.dto.course;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

@Schema(description = "AI 추천 더보기 요청")
public record CourseCurationRequest(
    @NotBlank @Schema(description = "출발지") String origin,
    @NotBlank @Schema(description = "도착지") String destination,
    @NotBlank @Schema(description = "테마") String theme,
    @NotBlank @Schema(description = "경로 요약") String routeSummary,
    @NotBlank @Schema(description = "추천 설명") String explanation,
    @NotNull @Size(min = 1, max = 10) @Valid
    @Schema(description = "추천 정차 장소 목록")
    List<CourseStopRequest> stops,
    @Schema(description = "추가 추천 개수", defaultValue = "2")
    Integer extraStops
) {
}
