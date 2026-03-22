package io.routepickapi.dto.recommendation;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "추천 경유지 요약")
public record RecommendedStopResponse(
    @Schema(description = "장소명", example = "북한산 전망대")
    String name,
    @Schema(description = "위도", example = "37.658")
    double lat,
    @Schema(description = "경도", example = "126.974")
    double lng,
    @Schema(description = "유형", example = "전망대")
    String type,
    @Schema(description = "태그 목록", example = "[\"전망\", \"nature\"]")
    List<String> tags,
    @Schema(description = "경관 점수", example = "0.9")
    double viewScore,
    @Schema(description = "드라이브 적합성 점수", example = "0.7")
    double driveSuitability
) {
}
