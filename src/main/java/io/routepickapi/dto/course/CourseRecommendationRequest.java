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
    @Schema(description = "분위기", allowableValues = {"야경", "감성", "힐링", "한적한"})
    java.util.List<String> moods,
    @Schema(description = "들를 곳", allowableValues = {"분좋카", "맛집", "전망대", "산책"})
    java.util.List<String> stopTypes,
    @Schema(description = "길 스타일", allowableValues = {"해안길", "산길", "와인딩", "무난한"})
    java.util.List<String> routeStyles,
    @Schema(description = "서비스 추천 여부")
    Boolean autoRecommend,
    @Min(2) @Max(4) @Schema(description = "추천 정차 수", defaultValue = "3")
    Integer maxStops,
    @Min(1) @Max(50) @Schema(description = "최대 우회 거리(km)", defaultValue = "10")
    Integer maxDetourKm
) {
}
