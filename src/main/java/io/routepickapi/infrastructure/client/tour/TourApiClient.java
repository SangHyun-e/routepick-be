package io.routepickapi.infrastructure.client.tour;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.routepickapi.common.error.CustomException;
import io.routepickapi.common.error.ErrorType;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
public class TourApiClient {

    private static final String BASE_URL = "https://apis.data.go.kr/B551011/KorService1";
    private static final String MOBILE_OS = "ETC";
    private static final String MOBILE_APP = "RoutePick";

    private final RestClient restClient = RestClient.create(BASE_URL);

    @Value("${tour.api.service-key:}")
    private String serviceKey;

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

        TourApiResponse response = restClient.get()
            .uri(uriBuilder -> {
                var builder = uriBuilder
                    .path("/locationBasedList1")
                    .queryParam("serviceKey", serviceKey)
                    .queryParam("MobileOS", MOBILE_OS)
                    .queryParam("MobileApp", MOBILE_APP)
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
    }

    private List<TourItem> extractItems(TourApiResponse response) {
        if (response == null || response.response() == null) {
            return Collections.emptyList();
        }

        Response payload = response.response();
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

    public record TourApiResponse(Response response) {
    }

    public record Response(Body body) {
    }

    public record Body(Items items) {
    }

    public record Items(List<TourItem> item) {
    }

    public record TourItem(
        String contentid,
        String contenttypeid,
        String title,
        String addr1,
        String addr2,
        String mapx,
        String mapy,
        String areacode,
        String sigungucode,
        String cat1,
        String cat2,
        String cat3,
        String firstimage,
        @JsonAlias("firstimage2") String firstImage2
    ) {
    }
}
