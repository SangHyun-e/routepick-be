package io.routepickapi.dto.course;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "AI 드라이브 코스 추천 요청")
public record CourseRecommendationRequest(
    @NotBlank @Schema(description = "출발지 주소")
    String origin,
    @NotBlank @Schema(description = "도착지 주소")
    String destination,
    @NotBlank @Schema(description = "테마", allowableValues = {"야경", "바다", "산", "카페", "맛집"})
    String theme,
    @Min(1) @Max(3) @Schema(description = "추천 정차 수", defaultValue = "3")
    Integer maxStops,
    @Min(1) @Max(50) @Schema(description = "최대 우회 거리(km)", defaultValue = "10")
    Integer maxDetourKm
) {
}
