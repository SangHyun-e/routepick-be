package io.routepickapi.infrastructure.client.routing;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.routepickapi.common.error.CustomException;
import io.routepickapi.common.error.ErrorType;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
public class RoutingClient {

    private static final String BASE_URL = "https://api.openrouteservice.org";

    private final RestClient restClient = RestClient.create(BASE_URL);

    @Value("${routing.api-key:}")
    private String apiKey;

    @Value("${routing.profile:driving-car}")
    private String profile;

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

        return restClient.post()
            .uri("/v2/matrix/" + profile)
            .header(HttpHeaders.AUTHORIZATION, apiKey)
            .body(request)
            .retrieve()
            .body(MatrixResponse.class);
    }

    public DirectionsResponse fetchDirections(List<Coordinate> coordinates) {
        validateApiKey();

        if (coordinates == null || coordinates.size() < 2) {
            throw new CustomException(ErrorType.COMMON_INVALID_INPUT, "directions 계산에는 2개 이상의 좌표가 필요합니다.");
        }

        DirectionsRequest request = new DirectionsRequest(
            coordinates.stream()
                .map(point -> List.of(point.longitude(), point.latitude()))
                .toList()
        );

        return restClient.post()
            .uri("/v2/directions/" + profile)
            .header(HttpHeaders.AUTHORIZATION, apiKey)
            .body(request)
            .retrieve()
            .body(DirectionsResponse.class);
    }

    private void validateApiKey() {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Routing API key missing");
            throw new CustomException(ErrorType.COMMON_INTERNAL, "Routing API 키가 필요합니다.");
        }
    }

    public record Coordinate(double longitude, double latitude) {
        public Coordinate {
            if (latitude < -90.0 || latitude > 90.0) {
                throw new IllegalArgumentException("latitude out of range");
            }
            if (longitude < -180.0 || longitude > 180.0) {
                throw new IllegalArgumentException("longitude out of range");
            }
        }
    }

    public record MatrixRequest(
        List<List<Double>> locations,
        List<String> metrics,
        String units
    ) {
    }

    public record MatrixResponse(
        List<List<Double>> durations,
        List<List<Double>> distances
    ) {
    }

    public record DirectionsRequest(List<List<Double>> coordinates) {
    }

    public record DirectionsResponse(List<Route> routes) {
    }

    public record Route(Summary summary) {
    }

    public record Summary(
        double distance,
        double duration,
        @JsonAlias("way_points") List<Integer> wayPoints
    ) {
    }
}
