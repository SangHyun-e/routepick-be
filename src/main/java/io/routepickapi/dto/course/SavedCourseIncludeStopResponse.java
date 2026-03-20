package io.routepickapi.dto.course;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "저장된 포함 경유지")
public record SavedCourseIncludeStopResponse(
    @Schema(description = "장소명") String name,
    @Schema(description = "위도") Double lat,
    @Schema(description = "경도") Double lng
) {
}
