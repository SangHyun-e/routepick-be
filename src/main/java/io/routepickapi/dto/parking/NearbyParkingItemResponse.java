package io.routepickapi.dto.parking;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "근처 주차장 정보")
public record NearbyParkingItemResponse(
    @Schema(description = "주차장 이름") String name,
    @Schema(description = "주소") String address,
    @Schema(description = "거리(미터)") int distanceMeters
) {
}
