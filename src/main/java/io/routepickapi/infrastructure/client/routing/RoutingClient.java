package io.routepickapi.infrastructure.client.routing;

import io.routepickapi.common.error.CustomException;
import io.routepickapi.common.error.ErrorType;
import io.routepickapi.infrastructure.client.routing.dto.Coordinate;
import io.routepickapi.infrastructure.client.routing.dto.DirectionsRequest;
import io.routepickapi.infrastructure.client.routing.dto.DirectionsResponse;
import io.routepickapi.infrastructure.client.routing.dto.MatrixRequest;
import io.routepickapi.infrastructure.client.routing.dto.MatrixResponse;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Slf4j
@Component
public class RoutingClient {
    private final RestClient restClient;
    private final String apiKey;
    private final String profile;

    public RoutingClient(
        RestClient.Builder builder,
        @Value("${external.routing.base-url}") String baseUrl,
        @Value("${external.routing.api-key:}") String apiKey,
        @Value("${external.routing.profile:driving-car}") String profile
    ) {
        this.restClient = builder.baseUrl(baseUrl).build();
        this.apiKey = apiKey;
        this.profile = profile;
    }

    public MatrixResponse fetchMatrix(List<Coordinate> coordinates) {
        validateApiKey();

        if (coordinates == null || coordinates.size() < 2) {
            throw new CustomException(ErrorType.COMMON_INVALID_INPUT, "matrix 계산에는 2개 이상의 좌표가 필요합니다.");
        }

        MatrixRequest request = new MatrixRequest(
            coordinates.stream()
                .map(point -> List.of(point.longitude(), point.latitude()))
                .toList(),
            List.of("duration", "distance"),
            "km"
        );

        try {
            return restClient.post()
                .uri("/v2/matrix/" + profile)
                .header(HttpHeaders.AUTHORIZATION, apiKey)
                .body(request)
                .retrieve()
                .body(MatrixResponse.class);
        } catch (RestClientResponseException exception) {
            log.warn(
                "Routing API matrix failed: status={}, body={}",
                exception.getStatusCode(),
                exception.getResponseBodyAsString()
            );
            return new MatrixResponse(List.of(), List.of());
        } catch (RestClientException exception) {
            log.warn("Routing API matrix failed");
            return new MatrixResponse(List.of(), List.of());
        }
    }

    public DirectionsResponse fetchDirections(List<Coordinate> coordinates) {
        DirectionsResult result = fetchDirectionsResult(coordinates);
        if (result.response() != null) {
            return result.response();
        }
        return new DirectionsResponse(List.of());
    }

    public DirectionsResult fetchDirectionsResult(List<Coordinate> coordinates) {
        validateApiKey();

        if (coordinates == null || coordinates.size() < 2) {
            throw new CustomException(ErrorType.COMMON_INVALID_INPUT, "directions 계산에는 2개 이상의 좌표가 필요합니다.");
        }

        DirectionsRequest request = new DirectionsRequest(
            coordinates.stream()
                .map(point -> List.of(point.longitude(), point.latitude()))
                .toList()
        );

        try {
            DirectionsResponse response = restClient.post()
                .uri("/v2/directions/" + profile)
                .header(HttpHeaders.AUTHORIZATION, apiKey)
                .body(request)
                .retrieve()
                .body(DirectionsResponse.class);
            return new DirectionsResult(response, null);
        } catch (RestClientResponseException exception) {
            log.warn(
                "Routing API directions failed: status={}, body={}",
                exception.getStatusCode(),
                exception.getResponseBodyAsString()
            );
            return new DirectionsResult(null, exception.getStatusCode().value());
        } catch (RestClientException exception) {
            log.warn("Routing API directions failed");
            return new DirectionsResult(null, null);
        }
    }

    private void validateApiKey() {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Routing API key missing");
            throw new CustomException(ErrorType.COMMON_INTERNAL, "Routing API 키가 필요합니다.");
        }
    }

    public record DirectionsResult(DirectionsResponse response, Integer statusCode) {
        public boolean isRateLimited() {
            return statusCode != null && statusCode == 429;
        }

        public boolean isNotFound() {
            return statusCode != null && statusCode == 404;
        }
    }

}
