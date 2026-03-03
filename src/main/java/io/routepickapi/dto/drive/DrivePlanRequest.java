package io.routepickapi.dto.drive;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "드라이브 코스 AI 추천 요청")
public record DrivePlanRequest(
    @NotBlank @Schema(description = "출발지 검색어")
    String startKeyword,
    @NotBlank @Schema(description = "도착지 검색어")
    String endKeyword,
    @NotBlank @Schema(description = "테마", allowableValues = {
        "HEALING",
        "WINDING",
        "NIGHT_VIEW",
        "CAFE",
        "SEA",
        "ETC"
    })
    String theme,
    @Schema(description = "현재 시간 기준 영업중 추정", defaultValue = "false")
    Boolean openNowOnly
) {
}
