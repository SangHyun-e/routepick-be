package io.routepickapi.infrastructure.client.kakao;

import io.routepickapi.common.error.CustomException;
import io.routepickapi.common.error.ErrorType;
import io.routepickapi.dto.place.KakaoPlaceSearchResponse;
import io.routepickapi.infrastructure.client.kakao.dto.KakaoCoordToAddressResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
public class KakaoLocalClient {
    private final RestClient restClient;
    private final String restApiKey;

    public KakaoLocalClient(
        RestClient.Builder builder,
        @Value("${external.kakao.base-url}") String baseUrl,
        @Value("${external.kakao.api-key:}") String restApiKey
    ) {
        this.restClient = builder.baseUrl(baseUrl).build();
        this.restApiKey = restApiKey;
    }

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

    public KakaoCoordToAddressResponse coordToAddress(double longitude, double latitude) {
        validateRestApiKey();

        return restClient.get()
            .uri(uriBuilder -> uriBuilder
                .path("/v2/local/geo/coord2address.json")
                .queryParam("x", longitude)
                .queryParam("y", latitude)
                .build())
            .header(HttpHeaders.AUTHORIZATION, "KakaoAK " + restApiKey)
            .retrieve()
            .body(KakaoCoordToAddressResponse.class);
    }

    private void validateRestApiKey() {
        if (restApiKey == null || restApiKey.isBlank()) {
            log.warn("Kakao REST API key missing");
            throw new CustomException(ErrorType.COMMON_INTERNAL, "Kakao REST API 키가 필요합니다.");
        }
    }

}
