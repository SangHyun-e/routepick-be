package io.routepickapi.infrastructure.client.overpass;

import io.routepickapi.common.error.CustomException;
import io.routepickapi.common.error.ErrorType;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Component
public class OverpassClient {

    private static final String BASE_URL = "https://overpass-api.de";

    private final RestClient restClient = RestClient.create(BASE_URL);

    @Value("${overpass.timeout-seconds:25}")
    private int timeoutSeconds;

    public OverpassResponse executeQuery(String query) {
        if (query == null || query.isBlank()) {
            throw new CustomException(ErrorType.COMMON_INVALID_INPUT, "Overpass query는 필수입니다.");
        }

        String normalized = normalizeQuery(query);
        String requestBody = "data=" + URLEncoder.encode(normalized, StandardCharsets.UTF_8);

        try {
            return restClient.post()
                .uri("/api/interpreter")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(requestBody)
                .retrieve()
                .body(OverpassResponse.class);
        } catch (RestClientException exception) {
            log.warn("Overpass API request failed", exception);
            throw new CustomException(ErrorType.COMMON_INTERNAL, "Overpass API 요청에 실패했습니다.");
        }
    }

    private String normalizeQuery(String query) {
        if (query.startsWith("[out:")) {
            return query;
        }
        return String.format("[out:json][timeout:%d];%s", timeoutSeconds, query);
    }

    public record OverpassResponse(List<OverpassElement> elements) {
    }

    public record OverpassElement(
        String type,
        long id,
        Double lat,
        Double lon,
        List<Long> nodes,
        Map<String, String> tags
    ) {
    }
}
