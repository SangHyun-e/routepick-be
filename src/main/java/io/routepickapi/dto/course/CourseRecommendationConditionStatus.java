package io.routepickapi.dto.course;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "추천 조건 적용 여부")
public record CourseRecommendationConditionStatus(
    @Schema(description = "조건 구분", example = "분위기") String category,
    @Schema(description = "조건 값", example = "힐링") String value,
    @Schema(description = "완화 여부") boolean relaxed
) {
}
