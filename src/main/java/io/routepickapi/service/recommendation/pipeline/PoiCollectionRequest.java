package io.routepickapi.service.recommendation.pipeline;

import java.util.List;

public record PoiCollectionRequest(
    double centerLat,
    double centerLng,
    int radiusMeters,
    List<String> kakaoKeywords,
    List<String> tourContentTypeIds
) {
}
