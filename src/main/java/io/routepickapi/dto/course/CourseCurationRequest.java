package io.routepickapi.dto.course;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

@Schema(description = "AI 추천 더보기 요청")
public record CourseCurationRequest(
    @NotBlank @Schema(description = "출발지") String origin,
    @NotBlank @Schema(description = "도착지") String destination,
    @Schema(description = "추천 조건 요약") String preferenceSummary,
    @Schema(description = "분위기", allowableValues = {"야경", "감성", "힐링", "한적한"})
    List<String> moods,
    @Schema(description = "들를 곳", allowableValues = {"분좋카", "맛집", "전망대", "산책"})
    List<String> stopTypes,
    @Schema(description = "길 스타일", allowableValues = {"해안길", "산길", "와인딩", "무난한"})
    List<String> routeStyles,
    @Schema(description = "서비스 추천 여부")
    Boolean autoRecommend,
    @NotBlank @Schema(description = "경로 요약") String routeSummary,
    @NotBlank @Schema(description = "추천 설명") String explanation,
    @NotNull @Size(min = 1, max = 10) @Valid
    @Schema(description = "추천 정차 장소 목록")
    List<CourseStopRequest> stops,
    @Schema(description = "추가 추천 개수", defaultValue = "2")
    Integer extraStops
) {
}
