package io.routepickapi.service.recommendation;

import io.routepickapi.dto.recommendation.GeoPoint;
import java.util.List;

public record RoutePath(List<GeoPoint> points, boolean routingBased) {

    public double totalDistanceKm() {
        if (points == null || points.size() < 2) {
            return 0.0;
        }
        double total = 0.0;
        for (int index = 1; index < points.size(); index++) {
            total += GeoUtils.distanceKm(points.get(index - 1), points.get(index));
        }
        return total;
    }

    public double distanceToPathKm(GeoPoint point) {
        if (points == null || points.size() < 2 || point == null) {
            return 0.0;
        }
        double minDistance = Double.MAX_VALUE;
        for (int index = 1; index < points.size(); index++) {
            double distance = GeoUtils.distancePointToSegmentKm(points.get(index - 1), points.get(index), point);
            minDistance = Math.min(minDistance, distance);
        }
        return minDistance == Double.MAX_VALUE ? 0.0 : minDistance;
    }

    public double progressRatio(GeoPoint point) {
        if (points == null || points.size() < 2 || point == null) {
            return 0.0;
        }

        double totalDistance = totalDistanceKm();
        if (totalDistance <= 0) {
            return 0.0;
        }

        double traveled = 0.0;
        double bestDistance = Double.MAX_VALUE;
        double bestProgress = 0.0;
        for (int index = 1; index < points.size(); index++) {
            GeoPoint start = points.get(index - 1);
            GeoPoint end = points.get(index);
            double segmentLength = GeoUtils.distanceKm(start, end);
            double ratio = projectionRatio(start, end, point);
            double distanceToSegment = GeoUtils.distancePointToSegmentKm(start, end, point);
            if (distanceToSegment < bestDistance) {
                bestDistance = distanceToSegment;
                bestProgress = traveled + segmentLength * ratio;
            }
            traveled += segmentLength;
        }
        return Math.max(0.0, Math.min(1.0, bestProgress / totalDistance));
    }

    public int segmentIndex(double ratio) {
        if (ratio < 0.34) {
            return 0;
        }
        if (ratio < 0.67) {
            return 1;
        }
        return 2;
    }

    private double projectionRatio(GeoPoint start, GeoPoint end, GeoPoint point) {
        double avgLat = Math.toRadians((start.y() + end.y() + point.y()) / 3.0);
        double startX = start.x() * Math.cos(avgLat);
        double startY = start.y();
        double endX = end.x() * Math.cos(avgLat);
        double endY = end.y();
        double pointX = point.x() * Math.cos(avgLat);
        double pointY = point.y();
        double dx = endX - startX;
        double dy = endY - startY;
        double denom = dx * dx + dy * dy;
        if (denom <= 0) {
            return 0.0;
        }
        double t = ((pointX - startX) * dx + (pointY - startY) * dy) / denom;
        return Math.max(0.0, Math.min(1.0, t));
    }
}
