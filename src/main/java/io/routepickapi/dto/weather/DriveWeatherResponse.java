package io.routepickapi.dto.weather;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "드라이브 날씨 메시지 응답")
public record DriveWeatherResponse(
    @Schema(description = "날씨 메시지") String message,
    @Schema(description = "현재 기온") Double temperature,
    @Schema(description = "강수 형태 코드") Integer precipitationType,
    @Schema(description = "하늘 상태 코드") Integer skyStatus,
    @Schema(description = "풍속") Double windSpeed,
    @Schema(description = "대체 위치 사용 여부") boolean usedFallbackLocation
) {
    public static DriveWeatherResponse fallback(boolean usedFallbackLocation) {
        return new DriveWeatherResponse(
            "🙌 날씨 정보를 불러오지 못했어요. 그래도 안전운전!",
            null,
            null,
            null,
            null,
            usedFallbackLocation
        );
    }

    public DriveWeatherResponse withUsedFallbackLocation(boolean usedFallbackLocation) {
        return new DriveWeatherResponse(
            message,
            temperature,
            precipitationType,
            skyStatus,
            windSpeed,
            usedFallbackLocation
        );
    }
}
