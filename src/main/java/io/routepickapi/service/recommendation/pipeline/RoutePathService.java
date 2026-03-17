package io.routepickapi.service.recommendation.pipeline;

import io.routepickapi.dto.recommendation.GeoPoint;
import io.routepickapi.infrastructure.client.routing.RoutingClient;
import io.routepickapi.infrastructure.client.routing.dto.Coordinate;
import io.routepickapi.infrastructure.client.routing.dto.DirectionsResponse;
import io.routepickapi.infrastructure.client.routing.dto.Route;
import io.routepickapi.service.recommendation.PolylineDecoder;
import io.routepickapi.service.recommendation.RoutePath;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoutePathService {

    private static final int MAX_POINTS = 120;

    private final RoutingClient routingClient;

    public RoutePath resolvePath(GeoPoint origin, GeoPoint destination) {
        if (origin == null || destination == null) {
            return new RoutePath(List.of(), false);
        }

        try {
            DirectionsResponse response = routingClient.fetchDirections(List.of(
                new Coordinate(origin.x(), origin.y()),
                new Coordinate(destination.x(), destination.y())
            ));
            if (response != null && response.routes() != null && !response.routes().isEmpty()) {
                Route route = response.routes().getFirst();
                if (route != null && route.geometry() != null && !route.geometry().isBlank()) {
                    String geometry = route.geometry().trim();
                    if (geometry.startsWith("[") || geometry.startsWith("{")) {
                        return new RoutePath(List.of(origin, destination), false);
                    }
                    List<GeoPoint> decoded = PolylineDecoder.decode(geometry);
                    List<GeoPoint> points = shrink(decoded);
                    if (points.size() >= 2) {
                        return new RoutePath(points, true);
                    }
                }
            }
        } catch (Exception ex) {
            log.debug("Routing path fallback", ex);
        }

        return new RoutePath(List.of(origin, destination), false);
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
