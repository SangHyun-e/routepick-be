package io.routepickapi.dto.recommendation;

import java.util.List;

public record ScoreDetail(
    double onTheWayScore,
    double scenicScore,
    double themeMatchScore,
    double driveSuitability,
    double categoryDiversity,
    double timeFit,
    double stopCountFit,
    double routeDeviationPenalty,
    double destinationDelayPenalty,
    double penalty,
    List<String> penaltyReasons
) {

    public static ScoreDetail empty() {
        return new ScoreDetail(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, List.of());
    }
}
