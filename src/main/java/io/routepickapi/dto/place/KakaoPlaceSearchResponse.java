package io.routepickapi.dto.place;

import com.fasterxml.jackson.annotation.JsonAlias;
import java.util.List;

public record KakaoPlaceSearchResponse(
    KakaoMeta meta,
    List<KakaoPlaceDocument> documents
) {

    public record KakaoMeta(
        @JsonAlias("is_end") boolean isEnd,
        @JsonAlias("pageable_count") int pageableCount,
        @JsonAlias("total_count") int totalCount
    ) {

    }

    public record KakaoPlaceDocument(
        String id,
        @JsonAlias("place_name") String placeName,
        @JsonAlias("category_name") String categoryName,
        @JsonAlias("category_group_code") String categoryGroupCode,
        @JsonAlias("category_group_name") String categoryGroupName,
        String phone,
        @JsonAlias("address_name") String addressName,
        @JsonAlias("road_address_name") String roadAddressName,
        @JsonAlias("place_url") String placeUrl,
        String x,
        String y,
        String distance
    ) {

    }
}
