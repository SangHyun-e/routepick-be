package io.routepickapi.repository;

import io.routepickapi.common.error.CustomException;
import io.routepickapi.common.error.ErrorType;
import io.routepickapi.weather.WeatherBaseTimeCalculator.BaseDateTime;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Slf4j
@Repository
public class KmaWeatherRepository implements WeatherRepository {

    private static final String BASE_URL = "https://apis.data.go.kr/1360000/VilageFcstInfoService_2.0";
    private static final int DEFAULT_ROWS = 1000;
    private static final int DEFAULT_PAGE = 1;

    private final RestClient restClient = RestClient.create(BASE_URL);

    @Value("${weather.kma.service-key:}")
    private String serviceKey;

    @Override
    public List<WeatherItem> fetchUltraShortNow(
        BaseDateTime baseDateTime,
        int nx,
        int ny
    ) {
        return request("/getUltraSrtNcst", baseDateTime, nx, ny);
    }

    @Override
    public List<WeatherItem> fetchUltraShortForecast(
        BaseDateTime baseDateTime,
        int nx,
        int ny
    ) {
        return request("/getUltraSrtFcst", baseDateTime, nx, ny);
    }

    private List<WeatherItem> request(
        String path,
        BaseDateTime baseDateTime,
        int nx,
        int ny
    ) {
        validateServiceKey();

        try {
            WeatherApiResponse response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                    .path(path)
                    .queryParam("serviceKey", serviceKey)
                    .queryParam("dataType", "JSON")
                    .queryParam("numOfRows", DEFAULT_ROWS)
                    .queryParam("pageNo", DEFAULT_PAGE)
                    .queryParam("base_date", baseDateTime.baseDate())
                    .queryParam("base_time", baseDateTime.baseTime())
                    .queryParam("nx", nx)
                    .queryParam("ny", ny)
                    .build())
                .retrieve()
                .body(WeatherApiResponse.class);

            if (response == null || response.response() == null) {
                return Collections.emptyList();
            }

            WeatherResponse payload = response.response();
            if (payload.header() == null || !"00".equals(payload.header().resultCode())) {
                log.warn("Weather API error: {}", payload.header());
                return Collections.emptyList();
            }

            if (payload.body() == null || payload.body().items() == null) {
                return Collections.emptyList();
            }

            List<WeatherItem> items = payload.body().items().item();
            return items == null ? Collections.emptyList() : items;
        } catch (RestClientResponseException ex) {
            log.warn("Weather API request failed: status={}", ex.getStatusCode(), ex);
            if (ex.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                throw new CustomException(ErrorType.COMMON_UNAUTHORIZED, "기상청 API 인증에 실패했습니다.");
            }
            return Collections.emptyList();
        }
    }

    private void validateServiceKey() {
        if (serviceKey == null || serviceKey.isBlank()) {
            throw new CustomException(ErrorType.COMMON_INTERNAL, "기상청 API 키가 설정되지 않았습니다.");
        }
    }

    public record WeatherApiResponse(WeatherResponse response) {
    }

    public record WeatherResponse(WeatherHeader header, WeatherBody body) {
    }

    public record WeatherHeader(String resultCode, String resultMsg) {
    }

    public record WeatherBody(WeatherItems items) {
    }

    public record WeatherItems(List<WeatherItem> item) {
    }
}
