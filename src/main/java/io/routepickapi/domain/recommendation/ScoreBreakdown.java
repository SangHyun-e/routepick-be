package io.routepickapi.domain.recommendation;

import java.util.List;

public record ScoreBreakdown(
    double sceneryScore,
    double driveScore,
    double diversityScore,
    double routeSmoothnessScore,
    double weatherScore,
    double penaltyScore,
    List<String> penaltyReasons
) {

    public ScoreBreakdown {
        penaltyReasons = penaltyReasons == null ? List.of() : List.copyOf(penaltyReasons);
    }

    public double totalScore() {
        return sceneryScore + driveScore + diversityScore + routeSmoothnessScore + weatherScore - penaltyScore;
    }
}
