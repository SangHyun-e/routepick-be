package io.routepickapi.service.recommendation.pipeline;

import java.util.List;

public record PoiCollectionRequest(
    double originLat,
    double originLng,
    double destinationLat,
    double destinationLng,
    int radiusMeters,
    List<String> kakaoKeywords,
    List<String> tourContentTypeIds
) {
}
