package io.routepickapi.mapper.recommendation;

import io.routepickapi.domain.recommendation.ScoreBreakdown;
import io.routepickapi.dto.recommendation.ScoreBreakdownResponse;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ScoreBreakdownResponseMapper {

    public ScoreBreakdownResponse map(ScoreBreakdown breakdown) {
        if (breakdown == null) {
            return new ScoreBreakdownResponse(0, 0, 0, 0, 0, 0, 0, List.of());
        }

        return new ScoreBreakdownResponse(
            breakdown.sceneryScore(),
            breakdown.driveScore(),
            breakdown.diversityScore(),
            breakdown.routeSmoothnessScore(),
            breakdown.weatherScore(),
            breakdown.penaltyScore(),
            breakdown.totalScore(),
            breakdown.penaltyReasons() == null ? List.of() : List.copyOf(breakdown.penaltyReasons())
        );
    }
}
