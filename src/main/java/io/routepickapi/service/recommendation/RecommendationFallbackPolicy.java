package io.routepickapi.service.recommendation;

import io.routepickapi.dto.recommendation.CandidatePlace;
import io.routepickapi.dto.recommendation.CourseCandidate;
import io.routepickapi.dto.recommendation.GeoPoint;
import io.routepickapi.dto.recommendation.ScoreDetail;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class RecommendationFallbackPolicy {

    public List<CourseCandidate> fallback(
        List<CandidatePlace> candidates,
        GeoPoint origin,
        GeoPoint destination,
        int minStops
    ) {
        if (candidates == null || candidates.size() < minStops) {
            return List.of();
        }

        List<CandidatePlace> sorted = new ArrayList<>(candidates);
        sorted.sort(Comparator.comparingDouble(place -> distanceKm(origin, place)));

        List<CandidatePlace> stops = sorted.stream().limit(minStops).toList();
        double totalDistanceKm = calculateTotalDistance(origin, destination, stops);
        int estimatedMinutes = GeoUtils.estimateMinutes(totalDistanceKm);

        CourseCandidate course = new CourseCandidate(
            UUID.randomUUID().toString(),
            stops,
            totalDistanceKm,
            estimatedMinutes,
            0,
            ScoreDetail.empty(),
            List.of("fallback_used")
        );
        return List.of(course);
    }

    private double calculateTotalDistance(
        GeoPoint origin,
        GeoPoint destination,
        List<CandidatePlace> stops
    ) {
        if (stops.isEmpty()) {
            return 0;
        }
        double distance = distanceKm(origin, stops.getFirst());
        for (int index = 0; index < stops.size() - 1; index++) {
            distance += distanceKm(stops.get(index), stops.get(index + 1));
        }
        distance += distanceKm(destination, stops.getLast());
        return distance;
    }

    private double distanceKm(GeoPoint origin, CandidatePlace place) {
        return GeoUtils.distanceKm(origin, new GeoPoint(place.x(), place.y()));
    }

    private double distanceKm(CandidatePlace first, CandidatePlace second) {
        return GeoUtils.distanceKm(
            new GeoPoint(first.x(), first.y()),
            new GeoPoint(second.x(), second.y())
        );
    }
}
