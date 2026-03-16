package io.routepickapi.dto.recommendation;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 점수 상세 응답 DTO.
 */
@Schema(description = "추천 점수 상세")
public record ScoreBreakdownResponse(
    @Schema(description = "경관 점수", example = "24.0")
    double sceneryScore,
    @Schema(description = "드라이브 적합성 점수", example = "20.0")
    double driveScore,
    @Schema(description = "카테고리 다양성 점수", example = "11.0")
    double diversityScore,
    @Schema(description = "동선 자연스러움 점수", example = "12.0")
    double routeSmoothnessScore,
    @Schema(description = "날씨 적합성 점수", example = "10.0")
    double weatherScore,
    @Schema(description = "패널티 점수", example = "5.0")
    double penaltyScore,
    @Schema(description = "총 점수", example = "72.0")
    double totalScore,
    @Schema(description = "패널티 사유", example = "[\"too-long\"]")
    List<String> penaltyReasons
) {
}
