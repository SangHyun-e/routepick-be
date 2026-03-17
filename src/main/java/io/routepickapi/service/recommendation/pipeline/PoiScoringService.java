package io.routepickapi.service.recommendation.pipeline;

import io.routepickapi.domain.poi.Poi;
import io.routepickapi.dto.recommendation.GeoPoint;
import io.routepickapi.service.recommendation.RouteMetrics;
import io.routepickapi.service.recommendation.RoutePath;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PoiScoringService {

    private static final double THEME_WEIGHT = 0.4;
    private static final double DISTANCE_WEIGHT = 0.25;
    private static final double PROGRESS_WEIGHT = 0.2;
    private static final double REVIEW_WEIGHT = 0.15;

    private final PoiThemePolicy themePolicy;

    public List<ScoredPoi> score(
        List<Poi> pois,
        DriveTheme theme,
        RouteMetrics routeMetrics,
        RoutePath routePath
    ) {
        if (pois == null || pois.isEmpty()) {
            return List.of();
        }
        return pois.stream()
            .map(poi -> buildScore(poi, theme, routeMetrics, routePath))
            .sorted((left, right) -> Double.compare(right.totalScore(), left.totalScore()))
            .toList();
    }

    public ScoredPoi scoreOne(Poi poi, DriveTheme theme, RouteMetrics routeMetrics, RoutePath routePath) {
        if (poi == null) {
            return new ScoredPoi(null, 0, 0, 0, 0, 0, 1, 0);
        }
        return buildScore(poi, theme, routeMetrics, routePath);
    }

    public double reviewScore(Poi poi) {
        if (poi == null) {
            return 0.0;
        }
        return (poi.viewScore() + poi.driveSuitability()) / 2.0;
    }

    private ScoredPoi buildScore(
        Poi poi,
        DriveTheme theme,
        RouteMetrics routeMetrics,
        RoutePath routePath
    ) {
        double themeScore = themePolicy.themeScore(poi, theme);
        double distanceScore = distanceScore(poi, routeMetrics, routePath);
        double progressScore = progressScore(poi, routePath);
        double reviewScore = reviewScore(poi);
        double totalScore =
            themeScore * THEME_WEIGHT
                + distanceScore * DISTANCE_WEIGHT
                + progressScore * PROGRESS_WEIGHT
                + reviewScore * REVIEW_WEIGHT;
        int segmentIndex = routePath == null
            ? 1
            : routePath.segmentIndex(progressScore);
        return new ScoredPoi(
            poi,
            totalScore,
            themeScore,
            distanceScore,
            progressScore,
            reviewScore,
            segmentIndex,
            sourcePriority(poi.source())
        );
    }

    private double distanceScore(Poi poi, RouteMetrics routeMetrics, RoutePath routePath) {
        if (poi == null || routePath == null || routeMetrics == null) {
            return 0.5;
        }
        GeoPoint point = new GeoPoint(poi.lng(), poi.lat());
        double distanceKm = routePath.distanceToPathKm(point);
        double radius = Math.max(1.0, routeMetrics.corridorRadiusKm());
        return Math.max(0.0, 1.0 - Math.min(1.0, distanceKm / radius));
    }

    private double progressScore(Poi poi, RoutePath routePath) {
        if (poi == null || routePath == null) {
            return 0.5;
        }
        GeoPoint point = new GeoPoint(poi.lng(), poi.lat());
        return routePath.progressRatio(point);
    }

    private int sourcePriority(String source) {
        if (source == null || source.isBlank()) {
            return 0;
        }
        String normalized = source.trim().toUpperCase(java.util.Locale.ROOT);
        if ("CURATED".equals(normalized)) {
            return 3;
        }
        if ("TOURAPI".equals(normalized)) {
            return 2;
        }
        if ("KAKAO".equals(normalized)) {
            return 1;
        }
        return 0;
    }

    public record ScoredPoi(
        Poi poi,
        double totalScore,
        double themeScore,
        double distanceScore,
        double progressScore,
        double reviewScore,
        int segmentIndex,
        int sourcePriority
    ) {
    }
}
