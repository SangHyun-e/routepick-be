package io.routepickapi.dto.place;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record KakaoPlaceSearchResponse(
    KakaoMeta meta,
    List<KakaoPlaceDocument> documents
) {

    public record KakaoMeta(
        @JsonProperty("is_end") boolean isEnd,
        @JsonProperty("pageable_count") int pageableCount,
        @JsonProperty("total_count") int totalCount
    ) {

    }

    public record KakaoPlaceDocument(
        String id,
        @JsonProperty("place_name") String placeName,
        @JsonProperty("category_name") String categoryName,
        @JsonProperty("category_group_code") String categoryGroupCode,
        @JsonProperty("category_group_name") String categoryGroupName,
        String phone,
        @JsonProperty("address_name") String addressName,
        @JsonProperty("road_address_name") String roadAddressName,
        @JsonProperty("place_url") String placeUrl,
        String x,
        String y,
        String distance
    ) {

    }
}
