package io.routepickapi.service.recommendation;

import io.routepickapi.dto.recommendation.GeoPoint;

public record RouteMetrics(
    double baseDistanceKm,
    double baseDurationMinutes,
    double maxDetourKm,
    double maxDelayMinutes,
    double corridorRadiusKm
) {

    public double estimatedDetourKm(GeoPoint origin, GeoPoint destination, GeoPoint waypoint) {
        double detour = GeoUtils.distanceKm(origin, waypoint) + GeoUtils.distanceKm(waypoint, destination) - baseDistanceKm;
        return Math.max(0.0, detour);
    }
}
