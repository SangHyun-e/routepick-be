package io.routepickapi.dto.course;

import io.routepickapi.entity.course.CourseRecommendationSave;
import io.routepickapi.entity.course.CourseRecommendationStop;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "추천 코스 저장 응답")
public record CourseRecommendationSaveResponse(
    @Schema(description = "저장 ID") Long id,
    @Schema(description = "출발지") String origin,
    @Schema(description = "도착지") String destination,
    @Schema(description = "테마") String theme,
    @Schema(description = "경로 요약") String routeSummary,
    @Schema(description = "추천 설명") String explanation,
    @Schema(description = "추천 정차 장소 목록") List<CourseStopResponse> stops,
    @Schema(description = "저장 시각") LocalDateTime createdAt
) {

    public static CourseRecommendationSaveResponse from(CourseRecommendationSave save) {
        List<CourseStopResponse> stops = save.getStops().stream()
            .map(CourseRecommendationSaveResponse::toStop)
            .toList();

        return new CourseRecommendationSaveResponse(
            save.getId(),
            save.getOrigin(),
            save.getDestination(),
            save.getTheme(),
            save.getRouteSummary(),
            save.getExplanation(),
            stops,
            save.getCreatedAt()
        );
    }

    private static CourseStopResponse toStop(CourseRecommendationStop stop) {
        return new CourseStopResponse(
            stop.getName(),
            stop.getAddress(),
            stop.getX(),
            stop.getY(),
            stop.getCategory()
        );
    }
}
