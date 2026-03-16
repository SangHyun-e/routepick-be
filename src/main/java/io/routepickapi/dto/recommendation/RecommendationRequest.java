package io.routepickapi.dto.recommendation;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * 드라이브 코스 추천 요청 DTO.
 */
@Schema(description = "드라이브 코스 추천 요청 파라미터")
public record RecommendationRequest(
    /** 출발 위도 (필수, -90~90 범위). */
    @Parameter(description = "출발 위도", example = "37.5665", required = true)
    @Schema(description = "출발 위도", example = "37.5665")
    @NotNull(message = "originLat는 필수입니다.")
    @DecimalMin(value = "-90.0", message = "originLat는 -90 이상이어야 합니다.")
    @DecimalMax(value = "90.0", message = "originLat는 90 이하이어야 합니다.")
    Double originLat,

    /** 출발 경도 (필수, -180~180 범위). */
    @Parameter(description = "출발 경도", example = "126.9780", required = true)
    @Schema(description = "출발 경도", example = "126.9780")
    @NotNull(message = "originLng는 필수입니다.")
    @DecimalMin(value = "-180.0", message = "originLng는 -180 이상이어야 합니다.")
    @DecimalMax(value = "180.0", message = "originLng는 180 이하이어야 합니다.")
    Double originLng,

    /**
     * 추천 테마 (한글/영문/숫자/공백/하이픈 허용, 최대 30자).
     */
    @Parameter(description = "코스 테마 (예: coastal, mountain, cafe)", example = "coastal")
    @Schema(description = "코스 테마", example = "coastal")
    @Size(max = 30, message = "theme은 30자 이하로 입력해주세요.")
    @Pattern(
        regexp = "^[A-Za-z0-9가-힣\s-]{1,30}$",
        message = "theme은 한글/영문/숫자/공백/하이픈만 입력 가능합니다."
    )
    String theme,

    /** 기대 드라이브 시간 (분 단위, 30~360). */
    @Parameter(description = "기대 드라이브 시간(분)", example = "180")
    @Schema(description = "기대 드라이브 시간(분)", example = "180")
    @Min(value = 30, message = "durationMinutes는 30 이상이어야 합니다.")
    @Max(value = 360, message = "durationMinutes는 360 이하이어야 합니다.")
    Integer durationMinutes,

    /** 출발 시간 (ISO-8601, 미래/현재 시간 권장). */
    @Parameter(description = "출발 시간(ISO-8601)", example = "2024-10-01T09:30:00")
    @Schema(description = "출발 시간", example = "2024-10-01T09:30:00")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    @FutureOrPresent(message = "departureTime은 현재 또는 미래여야 합니다.")
    LocalDateTime departureTime,

    /** 코스 최대 정차 수 (2~4). */
    @Parameter(description = "코스 최대 정차 수(2~4)", example = "3")
    @Schema(description = "코스 최대 정차 수", example = "3")
    @Min(value = 2, message = "maxStops는 2 이상이어야 합니다.")
    @Max(value = 4, message = "maxStops는 4 이하이어야 합니다.")
    Integer maxStops,

    /** 날씨 기반 보정 여부 (기본 true). */
    @Parameter(description = "날씨 기반 보정 사용 여부", example = "true")
    @Schema(description = "날씨 기반 보정 사용 여부", example = "true")
    Boolean weatherAware
) {
}
