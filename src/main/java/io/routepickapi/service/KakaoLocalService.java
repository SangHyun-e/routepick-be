package io.routepickapi.service;

import io.routepickapi.common.error.CustomException;
import io.routepickapi.common.error.ErrorType;
import io.routepickapi.dto.place.KakaoPlaceSearchResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Slf4j
@Service
public class KakaoLocalService {
    private final RestClient restClient;

    @Value("${external.kakao.api-key:}")
    private String restApiKey;

    public KakaoLocalService(
        RestClient.Builder builder,
        @Value("${external.kakao.base-url}") String baseUrl
    ) {
        this.restClient = builder.baseUrl(baseUrl).build();
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
        double x,
        double y,
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
                .queryParam("x", x)
                .queryParam("y", y)
                .queryParam("radius", safeRadius)
                .queryParam("page", safePage)
                .queryParam("size", safeSize)
                .build())
            .header(HttpHeaders.AUTHORIZATION, "KakaoAK " + restApiKey)
            .retrieve()
            .body(KakaoPlaceSearchResponse.class);
    }

    private void validateRestApiKey() {
        if (restApiKey == null || restApiKey.isBlank()) {
            log.warn("Kakao REST API key missing");
            throw new CustomException(ErrorType.COMMON_INTERNAL);
        }
    }
}
