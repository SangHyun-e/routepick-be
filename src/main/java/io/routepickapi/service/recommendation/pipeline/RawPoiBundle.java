package io.routepickapi.service.recommendation.pipeline;

import io.routepickapi.dto.place.KakaoPlaceSearchResponse.KakaoPlaceDocument;
import io.routepickapi.infrastructure.client.tour.dto.TourItem;
import java.util.List;

public record RawPoiBundle(
    List<KakaoPlaceDocument> kakaoPlaces,
    List<TourItem> tourItems
) {
}
