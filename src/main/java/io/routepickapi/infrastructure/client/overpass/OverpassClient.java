package io.routepickapi.infrastructure.client.overpass;

import io.routepickapi.common.error.CustomException;
import io.routepickapi.common.error.ErrorType;
import io.routepickapi.infrastructure.client.overpass.dto.OverpassResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Slf4j
@Component
public class OverpassClient {
    private final RestClient restClient;
    private final int timeoutSeconds;

    public OverpassClient(
        RestClient.Builder builder,
        @Value("${external.overpass.base-url}") String baseUrl,
        @Value("${external.overpass.timeout-seconds:25}") int timeoutSeconds
    ) {
        this.restClient = builder.baseUrl(baseUrl).build();
        this.timeoutSeconds = timeoutSeconds;
    }

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
        } catch (RestClientResponseException exception) {
            log.warn(
                "Overpass API request failed: status={}, body={}",
                exception.getStatusCode(),
                exception.getResponseBodyAsString()
            );
            return new OverpassResponse(List.of());
        } catch (RestClientException exception) {
            log.warn("Overpass API request failed");
            return new OverpassResponse(List.of());
        }
    }

    private String normalizeQuery(String query) {
        if (query.startsWith("[out:")) {
            return query;
        }
        return String.format("[out:json][timeout:%d];%s", timeoutSeconds, query);
    }

}
