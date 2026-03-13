package io.routepickapi.dto.recommendation;

import java.util.List;

public record ScoreDetail(
    double driveSuitability,
    double routeNaturalness,
    double timeFit,
    double categoryDiversity,
    double stopCountFit,
    double penalty,
    List<String> penaltyReasons
) {

    public static ScoreDetail empty() {
        return new ScoreDetail(0, 0, 0, 0, 0, 0, List.of());
    }
}
