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

    public static double distancePointToSegmentKm(GeoPoint start, GeoPoint end, GeoPoint point) {
        if (start == null || end == null || point == null) {
            return 0.0;
        }

        double avgLat = Math.toRadians((start.y() + end.y() + point.y()) / 3.0);
        double startX = Math.toRadians(start.x()) * Math.cos(avgLat) * EARTH_RADIUS_KM;
        double startY = Math.toRadians(start.y()) * EARTH_RADIUS_KM;
        double endX = Math.toRadians(end.x()) * Math.cos(avgLat) * EARTH_RADIUS_KM;
        double endY = Math.toRadians(end.y()) * EARTH_RADIUS_KM;
        double pointX = Math.toRadians(point.x()) * Math.cos(avgLat) * EARTH_RADIUS_KM;
        double pointY = Math.toRadians(point.y()) * EARTH_RADIUS_KM;

        double dx = endX - startX;
        double dy = endY - startY;
        if (dx == 0 && dy == 0) {
            return Math.hypot(pointX - startX, pointY - startY);
        }

        double t = ((pointX - startX) * dx + (pointY - startY) * dy) / (dx * dx + dy * dy);
        t = Math.max(0.0, Math.min(1.0, t));
        double projX = startX + t * dx;
        double projY = startY + t * dy;
        return Math.hypot(pointX - projX, pointY - projY);
    }
}
