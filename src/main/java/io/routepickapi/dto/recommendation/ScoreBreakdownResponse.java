package io.routepickapi.dto.recommendation;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 점수 상세 응답 DTO.
 */
@Schema(description = "추천 점수 상세")
public record ScoreBreakdownResponse(
    @Schema(description = "테마 점수", example = "30.0")
    double themeScore,
    @Schema(description = "경로 근접 점수", example = "22.0")
    double distanceScore,
    @Schema(description = "도착지 진행 점수", example = "18.0")
    double progressScore,
    @Schema(description = "장소 품질 점수", example = "12.0")
    double reviewScore,
    @Schema(description = "패널티 점수", example = "5.0")
    double penaltyScore,
    @Schema(description = "총 점수", example = "72.0")
    double totalScore,
    @Schema(description = "패널티 사유", example = "[\"too-long\"]")
    List<String> penaltyReasons
) {
}
