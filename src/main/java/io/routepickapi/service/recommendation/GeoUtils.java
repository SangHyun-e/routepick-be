package io.routepickapi.service.recommendation;

import io.routepickapi.dto.recommendation.GeoPoint;

public final class GeoUtils {

    private static final double EARTH_RADIUS_KM = 6371.0;
    private static final double AVERAGE_SPEED_KMH = 40.0;

    private GeoUtils() {
    }

    public static double distanceKm(GeoPoint start, GeoPoint end) {
        double startLat = Math.toRadians(start.y());
        double endLat = Math.toRadians(end.y());
        double deltaLat = Math.toRadians(end.y() - start.y());
        double deltaLon = Math.toRadians(end.x() - start.x());

        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
            + Math.cos(startLat) * Math.cos(endLat) * Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }

    public static int estimateMinutes(double distanceKm) {
        double hours = distanceKm / AVERAGE_SPEED_KMH;
        return Math.max(5, (int) Math.round(hours * 60));
    }
}
