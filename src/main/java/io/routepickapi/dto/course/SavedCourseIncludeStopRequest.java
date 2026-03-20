package io.routepickapi.dto.course;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "포함 요청 경유지")
public record SavedCourseIncludeStopRequest(
    @NotBlank @Schema(description = "장소명") String name,
    @NotNull @Schema(description = "위도") Double lat,
    @NotNull @Schema(description = "경도") Double lng
) {
}
