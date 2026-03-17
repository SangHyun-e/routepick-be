package io.routepickapi.infrastructure.client.weather;

import io.routepickapi.common.error.CustomException;
import io.routepickapi.common.error.ErrorType;
import io.routepickapi.infrastructure.client.weather.dto.WeatherApiResponse;
import io.routepickapi.infrastructure.client.weather.dto.WeatherItem;
import io.routepickapi.infrastructure.client.weather.dto.WeatherResponse;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Slf4j
@Component
public class WeatherClient {
    private static final int DEFAULT_ROWS = 1000;
    private static final int DEFAULT_PAGE = 1;
    private final RestClient restClient;
    private final String serviceKey;

    public WeatherClient(
        RestClient.Builder builder,
        @Value("${external.weather.kma.base-url}") String baseUrl,
        @Value("${external.weather.kma.service-key:}") String serviceKey
    ) {
        this.restClient = builder.baseUrl(baseUrl).build();
        this.serviceKey = serviceKey;
    }

    public List<WeatherItem> fetchUltraShortNow(String baseDate, String baseTime, int nx, int ny) {
        return request("/getUltraSrtNcst", baseDate, baseTime, nx, ny);
    }

    public List<WeatherItem> fetchUltraShortForecast(String baseDate, String baseTime, int nx, int ny) {
        return request("/getUltraSrtFcst", baseDate, baseTime, nx, ny);
    }

    private List<WeatherItem> request(
        String path,
        String baseDate,
        String baseTime,
        int nx,
        int ny
    ) {
        validateServiceKey();
        boolean encodedKey = serviceKey.contains("%");

        try {
            return executeRequest(path, baseDate, baseTime, nx, ny, serviceKey, encodedKey);
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                String alternate = resolveAlternateKey(encodedKey);
                if (alternate != null) {
                    boolean alternateEncoded = alternate.contains("%");
                    try {
                        return executeRequest(path, baseDate, baseTime, nx, ny, alternate, alternateEncoded);
                    } catch (RestClientResponseException retryException) {
                        log.warn("Weather API retry failed: status={}", retryException.getStatusCode(), retryException);
                    }
                }
                throw new CustomException(ErrorType.COMMON_UNAUTHORIZED, "기상청 API 인증에 실패했습니다.");
            }

            log.warn("Weather API request failed: status={}", exception.getStatusCode(), exception);
            return Collections.emptyList();
        }
    }

    private List<WeatherItem> executeRequest(
        String path,
        String baseDate,
        String baseTime,
        int nx,
        int ny,
        String key,
        boolean encodedKey
    ) {
        WeatherApiResponse response = restClient.get()
            .uri(uriBuilder -> uriBuilder
                .path(path)
                .queryParam("serviceKey", key)
                .queryParam("dataType", "JSON")
                .queryParam("numOfRows", DEFAULT_ROWS)
                .queryParam("pageNo", DEFAULT_PAGE)
                .queryParam("base_date", baseDate)
                .queryParam("base_time", baseTime)
                .queryParam("nx", nx)
                .queryParam("ny", ny)
                .build(encodedKey))
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
    }

    private String resolveAlternateKey(boolean encodedKey) {
        try {
            if (encodedKey) {
                String decoded = URLDecoder.decode(serviceKey, StandardCharsets.UTF_8);
                return decoded.equals(serviceKey) ? null : decoded;
            }
            return URLEncoder.encode(serviceKey, StandardCharsets.UTF_8);
        } catch (Exception exception) {
            return null;
        }
    }

    private void validateServiceKey() {
        if (serviceKey == null || serviceKey.isBlank()) {
            throw new CustomException(ErrorType.COMMON_INTERNAL, "기상청 API 키가 설정되지 않았습니다.");
        }
    }

}
