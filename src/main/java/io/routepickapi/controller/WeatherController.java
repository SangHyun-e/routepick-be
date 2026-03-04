package io.routepickapi.controller;

import io.routepickapi.dto.weather.DriveWeatherResponse;
import io.routepickapi.service.DriveWeatherService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/weather")
public class WeatherController {

    private final DriveWeatherService driveWeatherService;

    @Operation(summary = "드라이브 날씨 메시지", description = "위치 기반 드라이브 날씨 메시지를 제공합니다.")
    @GetMapping("/drive-message")
    public DriveWeatherResponse driveMessage(
        @Parameter(description = "위도") @RequestParam(required = false) Double lat,
        @Parameter(description = "경도") @RequestParam(required = false) Double lng,
        @Parameter(description = "대체 위치 사용 여부")
        @RequestParam(defaultValue = "false") boolean fallback
    ) {
        DriveWeatherResponse response = driveWeatherService.getDriveMessage(lat, lng, fallback);
        log.info("GET /weather/drive-message - fallback={}, message={}", fallback, response.message());
        return response;
    }
}
