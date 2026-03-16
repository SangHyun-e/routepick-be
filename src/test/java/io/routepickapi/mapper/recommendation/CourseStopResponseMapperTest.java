package io.routepickapi.mapper.recommendation;

import static org.assertj.core.api.Assertions.assertThat;

import io.routepickapi.domain.course.CourseStop;
import io.routepickapi.domain.poi.Poi;
import io.routepickapi.dto.recommendation.CourseStopResponse;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CourseStopResponseMapperTest {

    private final CourseStopResponseMapper mapper = new CourseStopResponseMapper();

    @Test
    void map_sortsTagsAndHandlesNullDurations() {
        // given
        Poi poi = new Poi(
            "KAKAO",
            "poi-1",
            "테스트 전망대",
            37.658,
            126.974,
            "전망대",
            Set.of("beta", "alpha", "gamma"),
            false,
            0.9,
            0.2,
            null,
            0.7
        );
        CourseStop stop = new CourseStop(0, poi, null, 10.5, null);

        // when
        CourseStopResponse response = mapper.map(stop);

        // then
        assertThat(response.name()).isEqualTo("테스트 전망대");
        assertThat(response.tags()).containsExactly("alpha", "beta", "gamma");
        assertThat(response.stayMinutes()).isZero();
        assertThat(response.segmentDurationMinutes()).isZero();
    }

    @Test
    void map_returnsDefault_whenStopIsNull() {
        // when
        CourseStopResponse response = mapper.map(null);

        // then
        assertThat(response.name()).isNull();
        assertThat(response.tags()).isEmpty();
        assertThat(response.segmentDistanceKm()).isZero();
    }
}
