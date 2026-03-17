package io.routepickapi.mapper.recommendation;

import io.routepickapi.domain.course.Course;
import io.routepickapi.domain.course.CourseStop;
import io.routepickapi.dto.recommendation.CourseStopResponse;
import io.routepickapi.dto.recommendation.CourseSummaryResponse;
import io.routepickapi.dto.recommendation.ScoreBreakdownResponse;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CourseSummaryResponseMapper {

    private final CourseStopResponseMapper courseStopResponseMapper;
    private final ScoreBreakdownResponseMapper scoreBreakdownResponseMapper;

    public CourseSummaryResponse map(Course course) {
        if (course == null) {
            return new CourseSummaryResponse(null, null, null, null, null, 0, 0, 0, null, List.of());
        }

        List<CourseStopResponse> stops = course.stops() == null
            ? List.of()
            : course.stops().stream().map(courseStopResponseMapper::map).toList();

        ScoreBreakdownResponse breakdown = scoreBreakdownResponseMapper.map(course.scoreBreakdown());
        Duration duration = course.totalDuration() == null ? Duration.ZERO : course.totalDuration();

        String title = buildTitle(course);
        String description = buildDescription(course.stops());

        return new CourseSummaryResponse(
            course.id(),
            course.region(),
            course.theme(),
            title,
            description,
            course.totalDistanceKm(),
            duration.toMinutes(),
            course.totalScore(),
            breakdown,
            stops
        );
    }

    private String buildTitle(Course course) {
        if (course == null || course.theme() == null || course.theme().isBlank()) {
            return "추천 드라이브 코스";
        }
        return course.theme() + " 추천 코스";
    }

    private String buildDescription(List<CourseStop> stops) {
        if (stops == null || stops.isEmpty()) {
            return "출발지에서 도착지까지 이동합니다.";
        }
        List<String> names = stops.stream()
            .map(CourseStop::poi)
            .filter(poi -> poi != null && poi.name() != null && !poi.name().isBlank())
            .map(poi -> poi.name().trim())
            .limit(3)
            .collect(Collectors.toList());
        if (names.isEmpty()) {
            return "출발지에서 도착지까지 이동합니다.";
        }
        return String.join(", ", names) + "을(를) 들러 이동합니다.";
    }
}
