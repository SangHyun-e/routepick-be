package io.routepickapi.dto.course;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "드라이브 코스 추천 응답")
public record CourseRecommendationResponse(
    @Schema(description = "추천 정차 장소 목록") List<CourseStopResponse> stops,
    @Schema(description = "경로 요약") String routeSummary,
    @Schema(description = "추천 설명") String explanation,
    @Schema(description = "조건 완화 정보") CourseRecommendationRelaxation relaxation
) {
}
