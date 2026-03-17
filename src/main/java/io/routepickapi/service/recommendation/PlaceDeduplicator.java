package io.routepickapi.service.recommendation;

import io.routepickapi.dto.recommendation.CandidatePlace;
import io.routepickapi.dto.recommendation.GeoPoint;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class PlaceDeduplicator {

    private static final double DUPLICATE_DISTANCE_KM = 0.15;

    public List<CandidatePlace> deduplicate(List<CandidatePlace> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        Map<String, CandidatePlace> byName = new LinkedHashMap<>();
        for (CandidatePlace candidate : candidates) {
            if (candidate == null) {
                continue;
            }
            CandidatePlace existing = byName.get(candidate.normalizedName());
            if (existing == null || isPreferred(candidate, existing)) {
                byName.put(candidate.normalizedName(), candidate);
            }
        }

        List<CandidatePlace> result = new ArrayList<>();
        for (CandidatePlace candidate : byName.values()) {
            CandidatePlace duplicate = result.stream()
                .filter(existing -> distanceKm(existing, candidate) <= DUPLICATE_DISTANCE_KM)
                .findFirst()
                .orElse(null);
            if (duplicate == null) {
                result.add(candidate);
                continue;
            }
            if (isPreferred(candidate, duplicate)) {
                result.remove(duplicate);
                result.add(candidate);
            }
        }

        return result;
    }

    private double distanceKm(CandidatePlace first, CandidatePlace second) {
        GeoPoint start = new GeoPoint(first.x(), first.y());
        GeoPoint end = new GeoPoint(second.x(), second.y());
        return GeoUtils.distanceKm(start, end);
    }

    private boolean isPreferred(CandidatePlace candidate, CandidatePlace other) {
        int candidatePriority = candidate.source() == null ? 0 : candidate.source().priority();
        int otherPriority = other.source() == null ? 0 : other.source().priority();
        if (candidatePriority != otherPriority) {
            return candidatePriority > otherPriority;
        }
        return candidate.safeTags().size() > other.safeTags().size();
    }
}
