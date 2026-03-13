package io.routepickapi.dto.course;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "추천 조건 완화 요약")
public record CourseRecommendationRelaxation(
    @Schema(description = "완화 여부") boolean relaxed,
    @Schema(description = "완화 안내 문구") String message,
    @Schema(description = "조건 상세") List<CourseRecommendationConditionStatus> conditions,
    @Schema(description = "검색 반경(미터)") int searchRadiusMeters,
    @Schema(description = "검색 반경 완화 여부") boolean searchRadiusRelaxed
) {
}
