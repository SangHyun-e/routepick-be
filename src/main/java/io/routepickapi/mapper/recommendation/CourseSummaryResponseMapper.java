package io.routepickapi.mapper.recommendation;

import io.routepickapi.domain.course.Course;
import io.routepickapi.dto.recommendation.CourseStopResponse;
import io.routepickapi.dto.recommendation.CourseSummaryResponse;
import io.routepickapi.dto.recommendation.ScoreBreakdownResponse;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CourseSummaryResponseMapper {

    private final CourseStopResponseMapper courseStopResponseMapper;
    private final ScoreBreakdownResponseMapper scoreBreakdownResponseMapper;

    public CourseSummaryResponse map(Course course) {
        if (course == null) {
            return new CourseSummaryResponse(null, null, null, 0, 0, 0, null, List.of());
        }

        List<CourseStopResponse> stops = course.stops() == null
            ? List.of()
            : course.stops().stream().map(courseStopResponseMapper::map).toList();

        ScoreBreakdownResponse breakdown = scoreBreakdownResponseMapper.map(course.scoreBreakdown());
        Duration duration = course.totalDuration() == null ? Duration.ZERO : course.totalDuration();

        return new CourseSummaryResponse(
            course.id(),
            course.region(),
            course.theme(),
            course.totalDistanceKm(),
            duration.toMinutes(),
            course.totalScore(),
            breakdown,
            stops
        );
    }
}
