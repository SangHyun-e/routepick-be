package io.routepickapi.repository;

import io.routepickapi.common.error.CustomException;
import io.routepickapi.common.error.ErrorType;
import io.routepickapi.weather.WeatherBaseTimeCalculator.BaseDateTime;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
    private static final int DEFAULT_ROWS = 1000;
    private static final int DEFAULT_PAGE = 1;
    private final RestClient restClient;

    @Value("${external.weather.kma.service-key:}")
    private String serviceKey;

    public KmaWeatherRepository(
        RestClient.Builder builder,
        @Value("${external.weather.kma.base-url}") String baseUrl
    ) {
        this.restClient = builder.baseUrl(baseUrl).build();
    }

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
        boolean encodedKey = serviceKey.contains("%");

        try {
            return executeRequest(path, baseDateTime, nx, ny, serviceKey, encodedKey);
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                String alternate = resolveAlternateKey(encodedKey);
                if (alternate != null) {
                    boolean alternateEncoded = alternate.contains("%");
                    try {
                        return executeRequest(path, baseDateTime, nx, ny, alternate, alternateEncoded);
                    } catch (RestClientResponseException retryEx) {
                        log.warn("Weather API retry failed: status={}", retryEx.getStatusCode(), retryEx);
                    }
                }
                throw new CustomException(ErrorType.COMMON_UNAUTHORIZED, "기상청 API 인증에 실패했습니다.");
            }

            log.warn("Weather API request failed: status={}", ex.getStatusCode(), ex);
            return Collections.emptyList();
        }
    }

    private List<WeatherItem> executeRequest(
        String path,
        BaseDateTime baseDateTime,
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
                .queryParam("base_date", baseDateTime.baseDate())
                .queryParam("base_time", baseDateTime.baseTime())
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
        } catch (Exception ex) {
            return null;
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
