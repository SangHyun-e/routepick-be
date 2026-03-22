package io.routepickapi.dto.course;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;

@Schema(description = "추천 코스 저장 요청")
public record CourseRecommendationSaveRequest(
    @NotBlank @Schema(description = "코스 제목") String title,
    @NotBlank @Schema(description = "코스 테마") String theme,
    @NotNull
    @DecimalMin(value = "-90.0", message = "originLat는 -90 이상이어야 합니다.")
    @DecimalMax(value = "90.0", message = "originLat는 90 이하이어야 합니다.")
    @Schema(description = "출발 위도") Double originLat,
    @NotNull
    @DecimalMin(value = "-180.0", message = "originLng는 -180 이상이어야 합니다.")
    @DecimalMax(value = "180.0", message = "originLng는 180 이하이어야 합니다.")
    @Schema(description = "출발 경도") Double originLng,
    @NotNull
    @DecimalMin(value = "-90.0", message = "destinationLat는 -90 이상이어야 합니다.")
    @DecimalMax(value = "90.0", message = "destinationLat는 90 이하이어야 합니다.")
    @Schema(description = "도착 위도") Double destinationLat,
    @NotNull
    @DecimalMin(value = "-180.0", message = "destinationLng는 -180 이상이어야 합니다.")
    @DecimalMax(value = "180.0", message = "destinationLng는 180 이하이어야 합니다.")
    @Schema(description = "도착 경도") Double destinationLng,
    @NotNull @Min(value = 30, message = "durationMinutes는 30 이상이어야 합니다.")
    @Max(value = 360, message = "durationMinutes는 360 이하이어야 합니다.")
    @Schema(description = "요청 드라이브 시간(분)") Integer durationMinutes,
    @NotNull @Min(value = 2, message = "maxStops는 2 이상이어야 합니다.")
    @Max(value = 4, message = "maxStops는 4 이하이어야 합니다.")
    @Schema(description = "요청 최대 경유지 수") Integer maxStops,
    @NotNull @DecimalMin(value = "0.0", message = "totalDistanceKm는 0 이상이어야 합니다.")
    @Schema(description = "총 거리(km)") Double totalDistanceKm,
    @NotNull @Min(value = 0, message = "totalDurationMinutes는 0 이상이어야 합니다.")
    @Schema(description = "총 소요 시간(분)") Long totalDurationMinutes,
    @NotBlank @Schema(description = "코스 설명") String description,
    @Schema(description = "AI 설명 텍스트") String explainText,
    @NotNull @Size(min = 1, max = 10) @Valid
    @Schema(description = "최종 선택 경유지 목록")
    List<SavedCourseStopRequest> selectedStops,
    @Valid @Schema(description = "포함 요청 경유지 목록")
    List<SavedCourseIncludeStopRequest> includeStops
) {
}
