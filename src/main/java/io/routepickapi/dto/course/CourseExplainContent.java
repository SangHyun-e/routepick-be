package io.routepickapi.dto.course;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "AI 코스 설명")
public record CourseExplainContent(
    @Schema(description = "설명 제목") String title,
    @Schema(description = "코스 설명") String description,
    @Schema(description = "추천 이유") String reason
) {
}
