package io.routepickapi.dto.course;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "AI 추천 더보기 응답")
public record CourseCurationResponse(
    @JsonProperty("course_title")
    @Schema(description = "코스 이름")
    String courseTitle,
    @JsonProperty("vibe_summary")
    @Schema(description = "코스 감성 요약")
    String vibeSummary,
    @JsonProperty("route_details")
    @Schema(description = "경로 상세")
    RouteDetails routeDetails,
    @JsonProperty("drive_info")
    @Schema(description = "운전 정보")
    DriveInfo driveInfo,
    @JsonProperty("curator_tips")
    @Schema(description = "큐레이터 팁")
    List<String> curatorTips,
    @JsonProperty("extra_stops")
    @Schema(description = "추가 추천 장소")
    List<CourseStopResponse> extraStops
) {
    public record RouteDetails(
        @Schema(description = "출발지") String start,
        @Schema(description = "경유지") String stopover,
        @Schema(description = "도착지") String destination
    ) {
    }

    public record DriveInfo(
        @Schema(description = "예상 소요 시간") String duration,
        @Schema(description = "운전 난이도") String difficulty,
        @JsonProperty("best_time")
        @Schema(description = "추천 출발 시간") String bestTime
    ) {
    }
}
