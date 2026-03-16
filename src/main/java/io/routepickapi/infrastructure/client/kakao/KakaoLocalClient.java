package io.routepickapi.infrastructure.client.kakao;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.routepickapi.common.error.CustomException;
import io.routepickapi.common.error.ErrorType;
import io.routepickapi.dto.place.KakaoPlaceSearchResponse;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
public class KakaoLocalClient {

    private static final String BASE_URL = "https://dapi.kakao.com";

    private final RestClient restClient = RestClient.create(BASE_URL);

    @Value("${kakao.rest-api-key:}")
    private String restApiKey;

    public KakaoPlaceSearchResponse searchKeyword(String keyword, int page, int size) {
        validateRestApiKey();

        if (keyword == null || keyword.isBlank()) {
            throw new CustomException(ErrorType.COMMON_INVALID_INPUT, "keyword는 필수입니다.");
        }

        int safePage = Math.max(1, Math.min(page, 45));
        int safeSize = Math.max(1, Math.min(size, 15));

        return restClient.get()
            .uri(uriBuilder -> uriBuilder
                .path("/v2/local/search/keyword.json")
                .queryParam("query", keyword)
                .queryParam("page", safePage)
                .queryParam("size", safeSize)
                .build())
            .header(HttpHeaders.AUTHORIZATION, "KakaoAK " + restApiKey)
            .retrieve()
            .body(KakaoPlaceSearchResponse.class);
    }

    public KakaoPlaceSearchResponse searchKeywordByLocation(
        String keyword,
        double longitude,
        double latitude,
        int radiusMeters,
        int page,
        int size
    ) {
        validateRestApiKey();

        if (keyword == null || keyword.isBlank()) {
            throw new CustomException(ErrorType.COMMON_INVALID_INPUT, "keyword는 필수입니다.");
        }

        int safePage = Math.max(1, Math.min(page, 45));
        int safeSize = Math.max(1, Math.min(size, 15));
        int safeRadius = Math.max(1000, Math.min(radiusMeters, 20000));

        return restClient.get()
            .uri(uriBuilder -> uriBuilder
                .path("/v2/local/search/keyword.json")
                .queryParam("query", keyword)
                .queryParam("x", longitude)
                .queryParam("y", latitude)
                .queryParam("radius", safeRadius)
                .queryParam("page", safePage)
                .queryParam("size", safeSize)
                .build())
            .header(HttpHeaders.AUTHORIZATION, "KakaoAK " + restApiKey)
            .retrieve()
            .body(KakaoPlaceSearchResponse.class);
    }

    public CoordToAddressResponse coordToAddress(double longitude, double latitude) {
        validateRestApiKey();

        return restClient.get()
            .uri(uriBuilder -> uriBuilder
                .path("/v2/local/geo/coord2address.json")
                .queryParam("x", longitude)
                .queryParam("y", latitude)
                .build())
            .header(HttpHeaders.AUTHORIZATION, "KakaoAK " + restApiKey)
            .retrieve()
            .body(CoordToAddressResponse.class);
    }

    private void validateRestApiKey() {
        if (restApiKey == null || restApiKey.isBlank()) {
            log.warn("Kakao REST API key missing");
            throw new CustomException(ErrorType.COMMON_INTERNAL, "Kakao REST API 키가 필요합니다.");
        }
    }

    public record CoordToAddressResponse(List<CoordDocument> documents) {
    }

    public record CoordDocument(
        Address address,
        @JsonAlias("road_address") RoadAddress roadAddress
    ) {
    }

    public record Address(
        @JsonAlias("address_name") String addressName,
        @JsonAlias("region_1depth_name") String region1DepthName,
        @JsonAlias("region_2depth_name") String region2DepthName,
        @JsonAlias("region_3depth_name") String region3DepthName,
        @JsonAlias("main_address_no") String mainAddressNo,
        @JsonAlias("sub_address_no") String subAddressNo
    ) {
    }

    public record RoadAddress(
        @JsonAlias("address_name") String addressName,
        @JsonAlias("region_1depth_name") String region1DepthName,
        @JsonAlias("region_2depth_name") String region2DepthName,
        @JsonAlias("region_3depth_name") String region3DepthName,
        @JsonAlias("road_name") String roadName,
        @JsonAlias("main_building_no") String mainBuildingNo,
        @JsonAlias("sub_building_no") String subBuildingNo,
        @JsonAlias("building_name") String buildingName,
        @JsonAlias("zone_no") String zoneNo
    ) {
    }
}
