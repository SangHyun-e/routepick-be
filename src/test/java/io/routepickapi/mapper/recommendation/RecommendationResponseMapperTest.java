package io.routepickapi.mapper.recommendation;

import static org.assertj.core.api.Assertions.assertThat;

import io.routepickapi.domain.course.Course;
import io.routepickapi.domain.recommendation.ScoreBreakdown;
import io.routepickapi.dto.recommendation.RecommendationResponse;
import io.routepickapi.service.recommendation.pipeline.DriveCourseResult;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class RecommendationResponseMapperTest {

    private final RecommendationResponseMapper mapper = new RecommendationResponseMapper(
        new CourseSummaryResponseMapper(
            new CourseStopResponseMapper(),
            new ScoreBreakdownResponseMapper()
        )
    );

    @Test
    void map_returnsResponse_whenResultProvided() {
        // given
        Course course = new Course(
            1L,
            "서울특별시 중구",
            "coastal",
            20.0,
            Duration.ofMinutes(120),
            50.0,
            List.of(),
            new ScoreBreakdown(10, 10, 10, 10, 10, 0, null),
            null
        );
        LocalDateTime departure = LocalDateTime.of(2024, 10, 1, 9, 30);
        LocalDateTime generated = LocalDateTime.of(2024, 10, 1, 9, 29, 58);
        DriveCourseResult result = new DriveCourseResult(
            "req-20241001-001",
            37.5665,
            126.9780,
            departure,
            List.of(course),
            generated
        );

        // when
        RecommendationResponse response = mapper.map(result);

        // then
        assertThat(response.requestId()).isEqualTo("req-20241001-001");
        assertThat(response.courses()).hasSize(1);
        assertThat(response.generatedAt()).isEqualTo(generated);
    }

    @Test
    void map_returnsEmptyCourses_whenResultHasNoCourses() {
        // given
        DriveCourseResult result = new DriveCourseResult(
            "req-empty",
            37.5665,
            126.9780,
            LocalDateTime.of(2024, 10, 1, 9, 30),
            List.of(),
            LocalDateTime.of(2024, 10, 1, 9, 29, 58)
        );

        // when
        RecommendationResponse response = mapper.map(result);

        // then
        assertThat(response.courses()).isEmpty();
    }

    @Test
    void map_returnsDefault_whenResultIsNull() {
        // when
        RecommendationResponse response = mapper.map(null);

        // then
        assertThat(response.requestId()).isNull();
        assertThat(response.courses()).isEmpty();
    }
}
