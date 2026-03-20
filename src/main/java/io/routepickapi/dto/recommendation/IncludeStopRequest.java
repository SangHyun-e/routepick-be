package io.routepickapi.dto.recommendation;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "코스 포함 요청 정차 지점")
public record IncludeStopRequest(
    @Schema(description = "장소명", example = "인왕산 전망대")
    String name,

    @Schema(description = "위도", example = "37.5921")
    Double lat,

    @Schema(description = "경도", example = "126.9589")
    Double lng
) {
}
