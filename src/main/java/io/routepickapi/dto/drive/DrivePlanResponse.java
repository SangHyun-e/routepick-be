package io.routepickapi.dto.drive;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "드라이브 코스 AI 추천 응답")
public record DrivePlanResponse(
    @Schema(description = "코스 이름") String courseName,
    @Schema(description = "코스 설명") String planReason,
    @Schema(description = "추천 경유지") List<DrivePlanStopResponse> stops
) {
}
