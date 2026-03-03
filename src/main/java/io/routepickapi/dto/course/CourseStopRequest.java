package io.routepickapi.dto.course;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "추천 장소 저장 요청")
public record CourseStopRequest(
    @NotBlank @Schema(description = "장소 이름") String name,
    @NotBlank @Schema(description = "주소") String address,
    @NotNull @Schema(description = "경도") Double x,
    @NotNull @Schema(description = "위도") Double y,
    @NotBlank @Schema(description = "카테고리") String category
) {
}
