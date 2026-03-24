package io.routepickapi.service.recommendation.pipeline;

import io.routepickapi.dto.recommendation.GeoPoint;
import io.routepickapi.infrastructure.client.routing.KakaoRoutingClient;
import io.routepickapi.infrastructure.client.routing.KakaoRoutingClient.PathResult;
import io.routepickapi.infrastructure.client.routing.dto.Coordinate;
import io.routepickapi.service.recommendation.RoutePath;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoutePathService {

    private static final int MAX_POINTS = 120;
    private static final int MAX_DIRECTIONS_ATTEMPTS = 5;
    private static final int MAX_NOT_FOUND_ATTEMPTS = 2;
    private static final double SNAP_OFFSET = 0.001;
    private static final int ROUTE_CACHE_LIMIT = 200;

    private final KakaoRoutingClient routingClient;
    private final Map<String, RouteAttempt> routeCache = Collections.synchronizedMap(
        new LinkedHashMap<>() {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, RouteAttempt> eldest) {
                return size() > ROUTE_CACHE_LIMIT;
            }
        }
    );

    public RoutePath resolvePath(GeoPoint origin, GeoPoint destination) {
        if (origin == null || destination == null) {
            return new RoutePath(List.of(), false);
        }

        int attempts = 0;
        RouteAttempt directAttempt = fetchRouteAttempt(origin, destination);
        attempts++;
        if (directAttempt.path() != null) {
            return directAttempt.path();
        }
        if (directAttempt.rateLimited()) {
            log.info("Routing retry stopped - reason=rate_limited, attempts={}", attempts);
            return new RoutePath(List.of(origin, destination), false);
        }

        int notFoundCount = directAttempt.notFound() ? 1 : 0;
        SnappedRoute snapped = resolveSnappedPath(origin, destination, attempts, notFoundCount);
        if (snapped != null) {
            log.info("Routing destination snapped - attempts={}, origin=({}, {}), destination=({}, {}), snapped=({}, {})",
                snapped.attempts(),
                origin.y(), origin.x(), destination.y(), destination.x(),
                snapped.snapped().y(), snapped.snapped().x());
            return snapped.path();
        }

        return new RoutePath(List.of(origin, destination), false);
    }

    private RouteAttempt fetchRouteAttempt(GeoPoint origin, GeoPoint destination) {
        String cacheKey = buildCacheKey(origin, destination);
        RouteAttempt cached = routeCache.get(cacheKey);
        if (cached != null) {
            log.info("Routing directions cache hit - key={}, status={}", cacheKey, cached.statusLabel());
            return cached;
        }
        try {
            PathResult result = routingClient.fetchRoutePath(
                new Coordinate(origin.x(), origin.y()),
                new Coordinate(destination.x(), destination.y())
            );
            if (result.isBlocked()) {
                return RouteAttempt.ofRateLimited(result.statusCode());
            }
            if (result.points() == null || result.points().isEmpty()) {
                RouteAttempt failure = RouteAttempt.failure(result.statusCode());
                routeCache.put(cacheKey, failure);
                return failure;
            }
            List<GeoPoint> points = result.routingBased() ? shrink(result.points()) : result.points();
            if (points.size() >= 2) {
                RouteAttempt success = RouteAttempt.success(new RoutePath(points, result.routingBased()));
                routeCache.put(cacheKey, success);
                return success;
            }
            RouteAttempt failure = RouteAttempt.failure(result.statusCode());
            routeCache.put(cacheKey, failure);
            return failure;
        } catch (Exception ex) {
            log.debug("Routing path fallback", ex);
            RouteAttempt failure = RouteAttempt.failure(null);
            routeCache.put(cacheKey, failure);
            return failure;
        }
    }

    private SnappedRoute resolveSnappedPath(
        GeoPoint origin,
        GeoPoint destination,
        int attemptsUsed,
        int notFoundCount
    ) {
        if (destination == null) {
            return null;
        }
        List<GeoPoint> candidates = buildSnapCandidates(destination);
        int attempts = attemptsUsed;
        int notFound = notFoundCount;
        java.util.Set<String> attemptedKeys = new java.util.HashSet<>();
        for (GeoPoint candidate : candidates) {
            if (attempts >= MAX_DIRECTIONS_ATTEMPTS) {
                log.info("Routing retry stopped - reason=max_attempts, attempts={}", attempts);
                return null;
            }
            String key = buildCacheKey(origin, candidate);
            if (!attemptedKeys.add(key)) {
                continue;
            }
            RouteAttempt attempt = fetchRouteAttempt(origin, candidate);
            attempts++;
            if (attempt.rateLimited()) {
                log.info("Routing retry stopped - reason=rate_limited, attempts={}", attempts);
                return null;
            }
            if (attempt.notFound()) {
                notFound++;
                if (notFound >= MAX_NOT_FOUND_ATTEMPTS) {
                    log.info("Routing retry stopped - reason=not_found_repeat, attempts={}", attempts);
                    return null;
                }
            }
            if (attempt.path() != null) {
                return new SnappedRoute(candidate, attempt.path(), attempts);
            }
        }
        log.info("Routing snap failed - attempts={}, notFoundCount={}", attempts, notFound);
        return null;
    }

    private List<GeoPoint> buildSnapCandidates(GeoPoint destination) {
        double x = destination.x();
        double y = destination.y();
        return List.of(
            new GeoPoint(x + SNAP_OFFSET, y),
            new GeoPoint(x - SNAP_OFFSET, y),
            new GeoPoint(x, y + SNAP_OFFSET),
            new GeoPoint(x, y - SNAP_OFFSET)
        );
    }

    private record SnappedRoute(GeoPoint snapped, RoutePath path, int attempts) {
    }

    private record RouteAttempt(RoutePath path, Integer statusCode, boolean rateLimited) {
        private static RouteAttempt success(RoutePath path) {
            return new RouteAttempt(path, null, false);
        }

        private static RouteAttempt failure(Integer statusCode) {
            return new RouteAttempt(null, statusCode, false);
        }

        private static RouteAttempt ofRateLimited(Integer statusCode) {
            return new RouteAttempt(null, statusCode == null ? 429 : statusCode, true);
        }

        private boolean notFound() {
            return statusCode != null && statusCode == 404;
        }

        private String statusLabel() {
            if (rateLimited) {
                return "rate_limited";
            }
            if (statusCode == null) {
                return "ok";
            }
            return statusCode.toString();
        }
    }

    private String buildCacheKey(GeoPoint origin, GeoPoint destination) {
        return formatPoint(origin) + ":" + formatPoint(destination);
    }

    private String formatPoint(GeoPoint point) {
        if (point == null) {
            return "0.00000,0.00000";
        }
        double lat = Math.round(point.y() * 1000.0) / 1000.0;
        double lng = Math.round(point.x() * 1000.0) / 1000.0;
        return String.format("%.3f,%.3f", lat, lng);
    }

    private List<GeoPoint> shrink(List<GeoPoint> points) {
        if (points == null || points.size() <= MAX_POINTS) {
            return points == null ? List.of() : points;
        }
        double step = (points.size() - 1) / (double) (MAX_POINTS - 1);
        java.util.ArrayList<GeoPoint> sampled = new java.util.ArrayList<>();
        for (int index = 0; index < MAX_POINTS; index++) {
            int position = (int) Math.round(index * step);
            sampled.add(points.get(Math.min(position, points.size() - 1)));
        }
        return sampled;
    }
}
