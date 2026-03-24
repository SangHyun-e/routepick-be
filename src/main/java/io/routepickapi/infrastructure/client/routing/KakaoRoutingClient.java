package io.routepickapi.infrastructure.client.routing;

import io.routepickapi.common.error.CustomException;
import io.routepickapi.common.error.ErrorType;
import io.routepickapi.dto.recommendation.GeoPoint;
import io.routepickapi.infrastructure.client.routing.dto.Coordinate;
import io.routepickapi.infrastructure.client.routing.kakao.dto.KakaoDirectionsResponse;
import io.routepickapi.infrastructure.client.routing.kakao.dto.KakaoRoad;
import io.routepickapi.infrastructure.client.routing.kakao.dto.KakaoRoute;
import io.routepickapi.infrastructure.client.routing.kakao.dto.KakaoSection;
import io.routepickapi.infrastructure.client.routing.kakao.dto.KakaoSummary;
import java.util.ArrayList;
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
public class KakaoRoutingClient {

    private static final String AUTH_PREFIX = "KakaoAK ";

    private final RestClient restClient;
    private final String apiKey;

    public KakaoRoutingClient(
        RestClient.Builder builder,
        @Value("${external.routing.kakao-base-url}") String baseUrl,
        @Value("${external.routing.kakao-api-key:}") String apiKey
    ) {
        this.restClient = builder.baseUrl(baseUrl).build();
        this.apiKey = apiKey;
    }

    public SegmentResult fetchSegmentMetrics(Coordinate origin, Coordinate destination) {
        KakaoDirectionsResult result = fetchDirections(origin, destination);
        if (result.isBlocked()) {
            return new SegmentResult(0.0, 0.0, false, result.statusCode());
        }
        KakaoRoute route = firstRoute(result.response());
        if (route == null || route.summary() == null) {
            return new SegmentResult(0.0, 0.0, false, result.statusCode());
        }
        KakaoSummary summary = route.summary();
        double distanceKm = summary.distance() / 1000.0;
        double durationMinutes = summary.duration() <= 0 ? 0.0 : summary.duration() / 60.0;
        if (distanceKm <= 0 && durationMinutes <= 0) {
            return new SegmentResult(0.0, 0.0, false, result.statusCode());
        }
        return new SegmentResult(distanceKm, durationMinutes, true, result.statusCode());
    }

    public PathResult fetchRoutePath(Coordinate origin, Coordinate destination) {
        KakaoDirectionsResult result = fetchDirections(origin, destination);
        if (result.isBlocked()) {
            return new PathResult(List.of(), false, result.statusCode());
        }
        KakaoRoute route = firstRoute(result.response());
        if (route == null) {
            return new PathResult(List.of(), false, result.statusCode());
        }
        List<GeoPoint> points = extractPoints(route);
        if (points.size() < 2) {
            return new PathResult(List.of(toPoint(origin), toPoint(destination)), false, result.statusCode());
        }
        return new PathResult(points, true, result.statusCode());
    }

    private KakaoDirectionsResult fetchDirections(Coordinate origin, Coordinate destination) {
        validateApiKey();

        if (origin == null || destination == null) {
            throw new CustomException(ErrorType.COMMON_INVALID_INPUT, "directions 계산에는 2개 이상의 좌표가 필요합니다.");
        }

        try {
            KakaoDirectionsResponse response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                    .path("/v1/directions")
                    .queryParam("origin", formatCoordinate(origin))
                    .queryParam("destination", formatCoordinate(destination))
                    .queryParam("priority", "RECOMMEND")
                    .build())
                .header(HttpHeaders.AUTHORIZATION, AUTH_PREFIX + apiKey)
                .retrieve()
                .body(KakaoDirectionsResponse.class);
            return new KakaoDirectionsResult(response, null);
        } catch (RestClientResponseException exception) {
            log.warn(
                "Kakao routing failed: status={}, body={}",
                exception.getStatusCode(),
                exception.getResponseBodyAsString()
            );
            return new KakaoDirectionsResult(null, exception.getStatusCode().value());
        } catch (RestClientException exception) {
            log.warn("Kakao routing failed");
            return new KakaoDirectionsResult(null, null);
        }
    }

    private KakaoRoute firstRoute(KakaoDirectionsResponse response) {
        if (response == null || response.routes() == null || response.routes().isEmpty()) {
            return null;
        }
        return response.routes().getFirst();
    }

    private List<GeoPoint> extractPoints(KakaoRoute route) {
        if (route.sections() == null || route.sections().isEmpty()) {
            return List.of();
        }
        List<GeoPoint> points = new ArrayList<>();
        for (KakaoSection section : route.sections()) {
            if (section == null || section.roads() == null) {
                continue;
            }
            for (KakaoRoad road : section.roads()) {
                if (road == null || road.vertexes() == null || road.vertexes().size() < 2) {
                    continue;
                }
                List<Double> vertexes = road.vertexes();
                for (int index = 0; index + 1 < vertexes.size(); index += 2) {
                    Double x = vertexes.get(index);
                    Double y = vertexes.get(index + 1);
                    if (x == null || y == null) {
                        continue;
                    }
                    points.add(new GeoPoint(x, y));
                }
            }
        }
        return points;
    }

    private GeoPoint toPoint(Coordinate coordinate) {
        return new GeoPoint(coordinate.longitude(), coordinate.latitude());
    }

    private String formatCoordinate(Coordinate coordinate) {
        return coordinate.longitude() + "," + coordinate.latitude();
    }

    private void validateApiKey() {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Kakao routing API key missing");
            throw new CustomException(ErrorType.COMMON_INTERNAL, "Kakao routing API 키가 필요합니다.");
        }
    }

    private record KakaoDirectionsResult(KakaoDirectionsResponse response, Integer statusCode) {
        private boolean isBlocked() {
            return statusCode != null && (statusCode == 401 || statusCode == 403 || statusCode == 429);
        }
    }

    public record SegmentResult(
        double distanceKm,
        double durationMinutes,
        boolean routingSuccess,
        Integer statusCode
    ) {
        public boolean isBlocked() {
            return statusCode != null && (statusCode == 401 || statusCode == 403 || statusCode == 429);
        }
    }

    public record PathResult(
        List<GeoPoint> points,
        boolean routingBased,
        Integer statusCode
    ) {
        public boolean isBlocked() {
            return statusCode != null && (statusCode == 401 || statusCode == 403 || statusCode == 429);
        }
    }
}
