package io.routepickapi.domain.recommendation;

import java.util.List;

public record ScoreBreakdown(
    double themeScore,
    double distanceScore,
    double progressScore,
    double reviewScore,
    double penaltyScore,
    List<String> penaltyReasons
) {

    public ScoreBreakdown {
        penaltyReasons = penaltyReasons == null ? List.of() : List.copyOf(penaltyReasons);
    }

    public double totalScore() {
        return themeScore + distanceScore + progressScore + reviewScore - penaltyScore;
    }
}
