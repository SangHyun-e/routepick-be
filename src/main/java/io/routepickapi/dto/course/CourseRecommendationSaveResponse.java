package io.routepickapi.dto.course;

import io.routepickapi.entity.course.CourseRecommendationSave;
import io.routepickapi.entity.course.CourseRecommendationIncludeStop;
import io.routepickapi.entity.course.CourseRecommendationStop;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

@Schema(description = "추천 코스 저장 응답")
public record CourseRecommendationSaveResponse(
    @Schema(description = "저장 ID") Long id,
    @Schema(description = "코스 제목") String title,
    @Schema(description = "테마") String theme,
    @Schema(description = "출발 위도") Double originLat,
    @Schema(description = "출발 경도") Double originLng,
    @Schema(description = "도착 위도") Double destinationLat,
    @Schema(description = "도착 경도") Double destinationLng,
    @Schema(description = "요청 드라이브 시간(분)") Integer durationMinutes,
    @Schema(description = "요청 최대 경유지 수") Integer maxStops,
    @Schema(description = "총 거리(km)") Double totalDistanceKm,
    @Schema(description = "총 소요 시간(분)") Long totalDurationMinutes,
    @Schema(description = "코스 설명") String description,
    @Schema(description = "AI 설명 텍스트") String explainText,
    @Schema(description = "최종 선택 경유지 목록") List<SavedCourseStopResponse> selectedStops,
    @Schema(description = "포함 요청 경유지 목록") List<SavedCourseIncludeStopResponse> includeStops,
    @Schema(description = "저장 시각") LocalDateTime createdAt
) {

    public static CourseRecommendationSaveResponse from(CourseRecommendationSave save) {
        List<SavedCourseStopResponse> stops = new ArrayList<>();
        if (save.getStops() != null) {
            IntStream.range(0, save.getStops().size())
                .forEach(index -> stops.add(toStop(index, save.getStops().get(index))));
        }
        List<SavedCourseIncludeStopResponse> includeStops = save.getIncludeStops() == null
            ? List.of()
            : save.getIncludeStops().stream()
                .map(CourseRecommendationSaveResponse::toIncludeStop)
                .toList();

        return new CourseRecommendationSaveResponse(
            save.getId(),
            save.getTitle(),
            save.getTheme(),
            save.getOriginLat(),
            save.getOriginLng(),
            save.getDestinationLat(),
            save.getDestinationLng(),
            save.getDurationMinutes(),
            save.getMaxStops(),
            save.getTotalDistanceKm(),
            save.getTotalDurationMinutes(),
            save.getDescription(),
            save.getExplainText(),
            stops,
            includeStops,
            save.getCreatedAt()
        );
    }

    private static SavedCourseStopResponse toStop(int order, CourseRecommendationStop stop) {
        return new SavedCourseStopResponse(
            order,
            stop.getName(),
            stop.getLat(),
            stop.getLng(),
            stop.getType(),
            stop.getTags(),
            stop.getStayMinutes(),
            stop.getViewScore(),
            stop.getDriveSuitability(),
            stop.getSegmentDistanceKm(),
            stop.getSegmentDurationMinutes()
        );
    }

    private static SavedCourseIncludeStopResponse toIncludeStop(
        CourseRecommendationIncludeStop stop
    ) {
        return new SavedCourseIncludeStopResponse(stop.getName(), stop.getLat(), stop.getLng());
    }
}
