package io.routepickapi.dto.course;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "코스 설명 응답")
public record CourseExplainResponse(
    @Schema(description = "설명 제목") String title,
    @Schema(description = "코스 설명") String description,
    @Schema(description = "추천 이유") String reason,
    @Schema(description = "오늘 남은 호출 횟수") int remainingCount
) {
}
