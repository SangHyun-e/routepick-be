package io.routepickapi.controller;

import io.routepickapi.common.error.CustomException;
import io.routepickapi.common.error.ErrorType;
import io.routepickapi.dto.parking.NearbyParkingItemResponse;
import io.routepickapi.service.ParkingService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/parking")
public class ParkingController {

    private final ParkingService parkingService;

    @Operation(summary = "근처 주차장 조회", description = "좌표 주변 주차장 정보를 조회합니다.")
    @GetMapping("/nearby")
    public List<NearbyParkingItemResponse> getNearbyParking(
        @RequestParam @NotNull Double lat,
        @RequestParam @NotNull Double lng
    ) {
        validateLatLng(lat, lng);
        return parkingService.findNearby(lat, lng);
    }

    private void validateLatLng(Double lat, Double lng) {
        if (lat == null || lng == null) {
            throw new CustomException(ErrorType.COMMON_INVALID_INPUT, "좌표가 필요합니다.");
        }
        if (!Double.isFinite(lat) || !Double.isFinite(lng)) {
            throw new CustomException(ErrorType.COMMON_INVALID_INPUT, "좌표가 올바르지 않습니다.");
        }
        if (lat < -90 || lat > 90 || lng < -180 || lng > 180) {
            throw new CustomException(ErrorType.COMMON_INVALID_INPUT, "좌표 범위가 올바르지 않습니다.");
        }
    }
}
