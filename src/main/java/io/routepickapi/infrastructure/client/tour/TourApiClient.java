package io.routepickapi.infrastructure.client.tour;

import io.routepickapi.common.error.CustomException;
import io.routepickapi.common.error.ErrorType;
import io.routepickapi.infrastructure.client.tour.dto.TourApiResponse;
import io.routepickapi.infrastructure.client.tour.dto.TourItem;
import io.routepickapi.infrastructure.client.tour.dto.TourResponse;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Slf4j
@Component
public class TourApiClient {
    private final RestClient restClient;
    private final String serviceKey;
    private final String mobileOs;
    private final String mobileApp;

    public TourApiClient(
        RestClient.Builder builder,
        @Value("${external.tourapi.base-url}") String baseUrl,
        @Value("${external.tourapi.service-key:}") String serviceKey,
        @Value("${external.tourapi.mobile-os:ETC}") String mobileOs,
        @Value("${external.tourapi.mobile-app:RoutePick}") String mobileApp
    ) {
        this.restClient = builder.baseUrl(baseUrl).build();
        this.serviceKey = serviceKey;
        this.mobileOs = mobileOs;
        this.mobileApp = mobileApp;
    }

    public List<TourItem> fetchLocationBased(
        double latitude,
        double longitude,
        int radiusMeters,
        int page,
        int size,
        String contentTypeId
    ) {
        validateServiceKey();

        int safeRadius = Math.max(1000, Math.min(radiusMeters, 20000));
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, Math.min(size, 100));
        boolean encodedKey = serviceKey.contains("%");

        try {
            TourApiResponse response = restClient.get()
                .uri(uriBuilder -> {
                    var builder = uriBuilder
                    .path("/locationBasedList2")
                        .queryParam("serviceKey", serviceKey)
                        .queryParam("MobileOS", mobileOs)
                        .queryParam("MobileApp", mobileApp)
                        .queryParam("_type", "json")
                        .queryParam("numOfRows", safeSize)
                        .queryParam("pageNo", safePage)
                        .queryParam("mapX", longitude)
                        .queryParam("mapY", latitude)
                        .queryParam("radius", safeRadius);
                    if (contentTypeId != null && !contentTypeId.isBlank()) {
                        builder.queryParam("contentTypeId", contentTypeId);
                    }
                    return builder.build(encodedKey);
                })
                .retrieve()
                .body(TourApiResponse.class);

            return extractItems(response);
        } catch (RestClientResponseException exception) {
            log.debug(
                "Tour API request failed: status={}, body={}",
                exception.getStatusCode(),
                exception.getResponseBodyAsString()
            );
            return Collections.emptyList();
        } catch (RestClientException exception) {
            log.debug("Tour API request failed");
            return Collections.emptyList();
        }
    }

    private List<TourItem> extractItems(TourApiResponse response) {
        if (response == null || response.response() == null) {
            return Collections.emptyList();
        }

        TourResponse payload = response.response();
        if (payload.body() == null || payload.body().items() == null) {
            return Collections.emptyList();
        }

        List<TourItem> items = payload.body().items().item();
        return items == null ? Collections.emptyList() : items;
    }

    private void validateServiceKey() {
        if (serviceKey == null || serviceKey.isBlank()) {
            log.warn("Tour API service key missing");
            throw new CustomException(ErrorType.COMMON_INTERNAL, "TourAPI 키가 필요합니다.");
        }
    }

}
