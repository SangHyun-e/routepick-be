package io.routepickapi.service.recommendation;

import io.routepickapi.dto.recommendation.CandidatePlace;
import io.routepickapi.dto.recommendation.GeoPoint;
import io.routepickapi.infrastructure.client.routing.KakaoRoutingClient;
import io.routepickapi.infrastructure.client.routing.KakaoRoutingClient.SegmentResult;
import io.routepickapi.infrastructure.client.routing.dto.Coordinate;
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

    private final KakaoRoutingClient routingClient;
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
        List<GeoPoint> legs = new ArrayList<>();
        legs.add(origin);
        if (stops != null) {
            for (CandidatePlace stop : stops) {
                legs.add(new GeoPoint(stop.x(), stop.y()));
            }
        }
        legs.add(destination);

        double distanceKm = 0.0;
        double durationMinutes = 0.0;
        boolean routingSuccess = true;
        for (int index = 1; index < legs.size(); index++) {
            GeoPoint from = legs.get(index - 1);
            GeoPoint to = legs.get(index);
            RouteLegMetrics segment = fetchSegmentMetrics(from, to);
            if (segment == null) {
                RouteLegMetrics result = fallbackMetrics(origin, destination, stops, false);
                log.info("Route metrics routing blocked - key={}", cacheKey);
                cacheService.putRouteMetrics(cacheKey, result);
                return result;
            }
            distanceKm += segment.distanceKm();
            durationMinutes += segment.durationMinutes();
            routingSuccess = routingSuccess && segment.routingSuccess();
        }

        if (distanceKm <= 0 && durationMinutes <= 0) {
            RouteLegMetrics result = fallbackMetrics(origin, destination, stops, false);
            log.info("Route metrics routing fallback - key={}", cacheKey);
            cacheService.putRouteMetrics(cacheKey, result);
            return result;
        }

        RouteLegMetrics result = new RouteLegMetrics(distanceKm, durationMinutes, routingSuccess);
        log.info("Route metrics routing success - key={}", cacheKey);
        cacheService.putRouteMetrics(cacheKey, result);
        return result;
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

    private RouteLegMetrics fetchSegmentMetrics(GeoPoint origin, GeoPoint destination) {
        String segmentKey = buildSegmentCacheKey(origin, destination);
        RouteLegMetrics cached = cacheService.getRouteMetrics(segmentKey);
        if (cached != null) {
            log.info("Route segment cache hit - key={}", segmentKey);
            return cached;
        }
        log.info("Route segment cache miss - key={}", segmentKey);
        SegmentResult result;
        try {
            result = routingClient.fetchSegmentMetrics(
                new Coordinate(origin.x(), origin.y()),
                new Coordinate(destination.x(), destination.y())
            );
        } catch (Exception ex) {
            log.debug("Routing segment fallback", ex);
            RouteLegMetrics fallback = fallbackSegmentMetrics(origin, destination, false);
            cacheService.putRouteMetrics(segmentKey, fallback);
            return null;
        }
        if (result == null) {
            return null;
        }
        if (result.isBlocked()) {
            RouteLegMetrics fallback = fallbackSegmentMetrics(origin, destination, false);
            cacheService.putRouteMetrics(segmentKey, fallback);
            return null;
        }
        RouteLegMetrics metrics;
        if (result.routingSuccess() && (result.distanceKm() > 0 || result.durationMinutes() > 0)) {
            metrics = new RouteLegMetrics(result.distanceKm(), result.durationMinutes(), true);
        } else {
            metrics = fallbackSegmentMetrics(origin, destination, false);
        }
        cacheService.putRouteMetrics(segmentKey, metrics);
        return metrics;
    }

    private RouteLegMetrics fallbackSegmentMetrics(
        GeoPoint origin,
        GeoPoint destination,
        boolean routingSuccess
    ) {
        double distanceKm = GeoUtils.distanceKm(origin, destination);
        return new RouteLegMetrics(distanceKm, GeoUtils.estimateMinutes(distanceKm), routingSuccess);
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

    private String buildSegmentCacheKey(GeoPoint origin, GeoPoint destination) {
        return new StringBuilder("route-metrics-segment:")
            .append(formatPoint(origin))
            .append(":")
            .append(formatPoint(destination))
            .toString();
    }

    private String formatPoint(GeoPoint point) {
        return String.format("%.5f,%.5f", point.y(), point.x());
    }

    public record RouteLegMetrics(double distanceKm, double durationMinutes, boolean routingSuccess) {
    }
}
