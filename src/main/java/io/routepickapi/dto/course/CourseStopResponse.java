package io.routepickapi.dto.course;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "추천 장소")
public record CourseStopResponse(
    @Schema(description = "장소 이름") String name,
    @Schema(description = "주소") String address,
    @Schema(description = "경도") double x,
    @Schema(description = "위도") double y,
    @Schema(description = "카테고리") String category
) {
}
