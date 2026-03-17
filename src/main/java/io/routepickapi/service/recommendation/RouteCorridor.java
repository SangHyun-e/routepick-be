package io.routepickapi.service.recommendation;

import io.routepickapi.dto.recommendation.GeoPoint;

public record RouteCorridor(
    GeoPoint origin,
    GeoPoint destination,
    RouteMetrics metrics
) {

    public boolean contains(GeoPoint point) {
        if (point == null || metrics == null) {
            return false;
        }
        double detourKm = metrics.estimatedDetourKm(origin, destination, point);
        double distanceToLineKm = GeoUtils.distancePointToSegmentKm(origin, destination, point);
        return detourKm <= metrics.maxDetourKm() && distanceToLineKm <= metrics.corridorRadiusKm();
    }

    public int searchRadiusMeters(int fallbackMeters) {
        if (metrics == null) {
            return fallbackMeters;
        }
        int radiusMeters = (int) Math.round(metrics.corridorRadiusKm() * 1000.0);
        return Math.max(fallbackMeters, radiusMeters);
    }
}
