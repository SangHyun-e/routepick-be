package io.routepickapi.dto.drive;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "드라이브 코스 경유지")
public record DrivePlanStopResponse(
    @Schema(description = "순서") int order,
    @Schema(description = "장소 이름") String name,
    @Schema(description = "주소") String address,
    @Schema(description = "위도") double lat,
    @Schema(description = "경도") double lng,
    @Schema(description = "Kakao 장소 URL") String placeUrl,
    @Schema(description = "추천 이유") String reason,
    @Schema(description = "영업중 추정 여부(보장하지 않음)") boolean openNowEstimated
) {
}
