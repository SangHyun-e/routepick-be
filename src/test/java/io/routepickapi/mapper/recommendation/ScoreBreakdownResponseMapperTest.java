package io.routepickapi.mapper.recommendation;

import static org.assertj.core.api.Assertions.assertThat;

import io.routepickapi.domain.recommendation.ScoreBreakdown;
import io.routepickapi.dto.recommendation.ScoreBreakdownResponse;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScoreBreakdownResponseMapperTest {

    private final ScoreBreakdownResponseMapper mapper = new ScoreBreakdownResponseMapper();

    @Test
    void map_returnsResponse_whenBreakdownProvided() {
        // given
        ScoreBreakdown breakdown = new ScoreBreakdown(
            24.0,
            20.0,
            11.0,
            12.0,
            5.0,
            List.of("too-long")
        );

        // when
        ScoreBreakdownResponse response = mapper.map(breakdown);

        // then
        assertThat(response.themeScore()).isEqualTo(24.0);
        assertThat(response.distanceScore()).isEqualTo(20.0);
        assertThat(response.progressScore()).isEqualTo(11.0);
        assertThat(response.reviewScore()).isEqualTo(12.0);
        assertThat(response.penaltyScore()).isEqualTo(5.0);
        assertThat(response.totalScore()).isEqualTo(breakdown.totalScore());
        assertThat(response.penaltyReasons()).containsExactly("too-long");
    }

    @Test
    void map_returnsDefault_whenBreakdownIsNull() {
        // when
        ScoreBreakdownResponse response = mapper.map(null);

        // then
        assertThat(response.totalScore()).isZero();
        assertThat(response.penaltyReasons()).isEmpty();
    }

    @Test
    void map_returnsEmptyPenaltyReasons_whenPenaltyReasonsNull() {
        // given
        ScoreBreakdown breakdown = new ScoreBreakdown(
            10.0,
            10.0,
            10.0,
            10.0,
            1.0,
            null
        );

        // when
        ScoreBreakdownResponse response = mapper.map(breakdown);

        // then
        assertThat(response.penaltyReasons()).isEmpty();
    }
}
