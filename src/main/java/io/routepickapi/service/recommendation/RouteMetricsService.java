package io.routepickapi.service.recommendation;

import io.routepickapi.dto.recommendation.CandidatePlace;
import io.routepickapi.dto.recommendation.GeoPoint;
import io.routepickapi.infrastructure.client.routing.RoutingClient;
import io.routepickapi.infrastructure.client.routing.dto.Coordinate;
import io.routepickapi.infrastructure.client.routing.dto.MatrixResponse;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RouteMetricsService {

    private static final double MIN_CORRIDOR_RADIUS_KM = 6.0;
    private static final double CORRIDOR_RATIO = 0.4;

    private final RoutingClient routingClient;
    private final RecommendationCacheService cacheService;

    public RouteMetrics buildMetrics(GeoPoint origin, GeoPoint destination, double maxDetourKm) {
        return buildMetrics(origin, destination, maxDetourKm, null);
    }

    public RouteMetrics buildMetrics(
        GeoPoint origin,
        GeoPoint destination,
        double maxDetourKm,
        Double maxDistanceKm
    ) {
        RouteLegMetrics baseMetrics = calculateMetrics(origin, destination, List.of());
        double baseDistanceKm = baseMetrics.distanceKm() > 0
            ? baseMetrics.distanceKm()
            : GeoUtils.distanceKm(origin, destination);
        double baseDurationMinutes = baseMetrics.durationMinutes() > 0
            ? baseMetrics.durationMinutes()
            : GeoUtils.estimateMinutes(baseDistanceKm);
        double safeDetourKm = Math.max(1.0, maxDetourKm);
        double effectiveMaxDistanceKm = maxDistanceKm != null && maxDistanceKm > 0
            ? Math.max(baseDistanceKm, maxDistanceKm)
            : baseDistanceKm;
        double extraDistanceKm = Math.max(0.0, effectiveMaxDistanceKm - baseDistanceKm);
        double detourCapKm = Math.max(safeDetourKm, extraDistanceKm * 0.6);
        double corridorBaseKm = baseDistanceKm * CORRIDOR_RATIO + extraDistanceKm * 0.25;
        double corridorRadiusKm = Math.max(
            MIN_CORRIDOR_RADIUS_KM,
            Math.min(detourCapKm, corridorBaseKm)
        );
        double maxDelayMinutes = Math.max(10.0, GeoUtils.estimateMinutes(detourCapKm));
        return new RouteMetrics(
            baseDistanceKm,
            baseDurationMinutes,
            detourCapKm,
            maxDelayMinutes,
            corridorRadiusKm
        );
    }

    public RouteLegMetrics calculateMetrics(
        GeoPoint origin,
        GeoPoint destination,
        List<CandidatePlace> stops
    ) {
        String cacheKey = buildCacheKey(origin, destination, stops);
        RouteLegMetrics cached = cacheService.getRouteMetrics(cacheKey);
        if (cached != null) {
            log.info("Route metrics cache hit - key={}", cacheKey);
            return cached;
        }
        log.info("Route metrics cache miss - key={}", cacheKey);
        List<Coordinate> coordinates = new ArrayList<>();
        coordinates.add(new Coordinate(origin.x(), origin.y()));
        if (stops != null) {
            for (CandidatePlace stop : stops) {
                coordinates.add(new Coordinate(stop.x(), stop.y()));
            }
        }
        coordinates.add(new Coordinate(destination.x(), destination.y()));

        try {
            MatrixResponse matrix = routingClient.fetchMatrix(coordinates);
            if (matrix == null || matrix.distances() == null || matrix.durations() == null
                || matrix.distances().isEmpty() || matrix.durations().isEmpty()) {
                RouteLegMetrics result = fallbackMetrics(origin, destination, stops, false);
                log.info("Route metrics routing fallback - key={}", cacheKey);
                cacheService.putRouteMetrics(cacheKey, result);
                return result;
            }
            double distanceKm = sumSequential(matrix.distances());
            double durationSeconds = sumSequential(matrix.durations());
            double durationMinutes = durationSeconds <= 0 ? 0.0 : durationSeconds / 60.0;
            if (distanceKm <= 0 && durationMinutes <= 0) {
                RouteLegMetrics result = fallbackMetrics(origin, destination, stops, false);
                log.info("Route metrics routing fallback - key={}", cacheKey);
                cacheService.putRouteMetrics(cacheKey, result);
                return result;
            }
            RouteLegMetrics result = new RouteLegMetrics(distanceKm, durationMinutes, true);
            log.info("Route metrics routing success - key={}", cacheKey);
            cacheService.putRouteMetrics(cacheKey, result);
            return result;
        } catch (Exception ex) {
            log.debug("Routing metrics fallback", ex);
            RouteLegMetrics result = fallbackMetrics(origin, destination, stops, false);
            log.info("Route metrics routing fallback - key={}", cacheKey);
            cacheService.putRouteMetrics(cacheKey, result);
            return result;
        }
    }

    private RouteLegMetrics fallbackMetrics(
        GeoPoint origin,
        GeoPoint destination,
        List<CandidatePlace> stops,
        boolean routingSuccess
    ) {
        double distanceKm = 0.0;
        GeoPoint previous = origin;
        if (stops != null) {
            for (CandidatePlace stop : stops) {
                GeoPoint current = new GeoPoint(stop.x(), stop.y());
                distanceKm += GeoUtils.distanceKm(previous, current);
                previous = current;
            }
        }
        distanceKm += GeoUtils.distanceKm(previous, destination);
        return new RouteLegMetrics(distanceKm, GeoUtils.estimateMinutes(distanceKm), routingSuccess);
    }

    private double sumSequential(List<List<Double>> matrix) {
        if (matrix == null || matrix.size() < 2) {
            return 0.0;
        }
        double sum = 0.0;
        for (int index = 1; index < matrix.size(); index++) {
            sum += safeMatrixValue(matrix, index - 1, index);
        }
        return sum;
    }

    private double safeMatrixValue(List<List<Double>> matrix, int from, int to) {
        if (from < 0 || to < 0 || from >= matrix.size()) {
            return 0.0;
        }
        List<Double> row = matrix.get(from);
        if (row == null || to >= row.size() || row.get(to) == null) {
            return 0.0;
        }
        return row.get(to);
    }

    private String buildCacheKey(GeoPoint origin, GeoPoint destination, List<CandidatePlace> stops) {
        StringBuilder builder = new StringBuilder("route-metrics:")
            .append(formatPoint(origin))
            .append(":")
            .append(formatPoint(destination));
        if (stops != null && !stops.isEmpty()) {
            builder.append(":");
            for (CandidatePlace stop : stops) {
                builder.append(formatPoint(new GeoPoint(stop.x(), stop.y()))).append("|");
            }
        }
        return builder.toString();
    }

    private String formatPoint(GeoPoint point) {
        return String.format("%.5f,%.5f", point.y(), point.x());
    }

    public record RouteLegMetrics(double distanceKm, double durationMinutes, boolean routingSuccess) {
    }
}
