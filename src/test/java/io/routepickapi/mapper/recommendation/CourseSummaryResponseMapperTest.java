package io.routepickapi.mapper.recommendation;

import static org.assertj.core.api.Assertions.assertThat;

import io.routepickapi.domain.course.Course;
import io.routepickapi.domain.course.CourseStop;
import io.routepickapi.domain.poi.Poi;
import io.routepickapi.domain.recommendation.ScoreBreakdown;
import io.routepickapi.dto.recommendation.CourseSummaryResponse;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CourseSummaryResponseMapperTest {

    private final CourseSummaryResponseMapper mapper = new CourseSummaryResponseMapper(
        new CourseStopResponseMapper(),
        new ScoreBreakdownResponseMapper()
    );

    @Test
    void map_returnsSummary_whenCourseProvided() {
        // given
        Poi poi = new Poi(
            "TOURAPI",
            "poi-2",
            "호수 공원",
            37.45,
            127.02,
            "공원",
            Set.of("lake", "park"),
            false,
            0.8,
            0.2,
            Duration.ofMinutes(45),
            0.6
        );
        CourseStop stop = new CourseStop(0, poi, null, 0.0, null);
        ScoreBreakdown breakdown = new ScoreBreakdown(10, 10, 10, 10, 10, 2, List.of("too-long"));
        Course course = new Course(
            1L,
            "서울특별시 중구",
            "coastal",
            55.2,
            Duration.ofMinutes(180),
            72.0,
            List.of(stop),
            breakdown,
            null
        );

        // when
        CourseSummaryResponse response = mapper.map(course);

        // then
        assertThat(response.courseId()).isEqualTo(1L);
        assertThat(response.region()).isEqualTo("서울특별시 중구");
        assertThat(response.stops()).hasSize(1);
        assertThat(response.scoreBreakdown().totalScore()).isEqualTo(breakdown.totalScore());
    }

    @Test
    void map_returnsEmptyStops_whenCourseHasNoStops() {
        // given
        Course course = new Course(
            2L,
            "서울특별시 중구",
            "city",
            10.0,
            null,
            0.0,
            null,
            new ScoreBreakdown(0, 0, 0, 0, 0, 0, null),
            null
        );

        // when
        CourseSummaryResponse response = mapper.map(course);

        // then
        assertThat(response.totalDurationMinutes()).isZero();
        assertThat(response.stops()).isEmpty();
    }

    @Test
    void map_returnsDefault_whenCourseIsNull() {
        // when
        CourseSummaryResponse response = mapper.map(null);

        // then
        assertThat(response.courseId()).isNull();
        assertThat(response.stops()).isEmpty();
        assertThat(response.totalScore()).isZero();
    }
}
