package io.routepickapi.service.recommendation.pipeline;

import io.routepickapi.domain.poi.Poi;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class CourseGenerationService {

    private static final int MIN_STOPS = 2;
    private static final int MAX_STOPS = 4;
    private static final int MAX_CANDIDATES = 30;
    private static final int DEFAULT_LIMIT = 40;
    private static final int MAX_CANDIDATES_PER_TYPE = 8;
    private static final Set<String> SOURCE_TAGS = Set.of("osm", "kakao", "tourapi");
    private static final double SIMILARITY_THRESHOLD = 0.6;

    public List<CoursePlan> generate(List<Poi> pois, int maxStops, int limit) {
        if (pois == null || pois.isEmpty()) {
            return List.of();
        }

        int safeMaxStops = Math.max(MIN_STOPS, Math.min(maxStops, MAX_STOPS));
        int safeLimit = limit > 0 ? limit : DEFAULT_LIMIT;

        List<Poi> candidates = buildDiverseCandidates(pois);

        List<CoursePlan> plans = new ArrayList<>();
        for (int stopCount = MIN_STOPS; stopCount <= safeMaxStops; stopCount++) {
            buildCombinations(candidates, stopCount, 0, new ArrayList<>(), plans, safeLimit);
            if (plans.size() >= safeLimit) {
                break;
            }
        }

        log.info("코스 조합 생성 완료 - count={}", plans.size());
        return plans;
    }

    private void buildCombinations(
        List<Poi> candidates,
        int targetSize,
        int startIndex,
        List<Poi> current,
        List<CoursePlan> results,
        int limit
    ) {
        if (results.size() >= limit) {
            return;
        }
        if (current.size() == targetSize) {
            if (!isTooSimilar(current, results)) {
                results.add(new CoursePlan(List.copyOf(current)));
            }
            return;
        }

        for (int index = startIndex; index < candidates.size(); index++) {
            if (results.size() >= limit) {
                return;
            }
            current.add(candidates.get(index));
            buildCombinations(candidates, targetSize, index + 1, current, results, limit);
            current.remove(current.size() - 1);
        }
    }

    private double baseScore(Poi poi) {
        return poi.viewScore() + poi.driveSuitability();
    }

    private List<Poi> buildDiverseCandidates(List<Poi> pois) {
        List<Poi> sorted = pois.stream()
            .sorted(Comparator.comparingDouble(this::baseScore).reversed())
            .toList();

        Map<String, List<Poi>> grouped = sorted.stream()
            .collect(Collectors.groupingBy(this::resolveTypeKey, LinkedHashMap::new, Collectors.toList()));

        List<Poi> mixed = new ArrayList<>();
        for (List<Poi> group : grouped.values()) {
            if (group == null || group.isEmpty()) {
                continue;
            }
            int end = Math.min(MAX_CANDIDATES_PER_TYPE, group.size());
            mixed.addAll(group.subList(0, end));
        }

        return mixed.stream().limit(MAX_CANDIDATES).toList();
    }

    private String resolveTypeKey(Poi poi) {
        if (poi == null) {
            return "unknown";
        }
        if (poi.type() != null && !poi.type().isBlank()) {
            return poi.type().trim().toLowerCase(Locale.ROOT);
        }
        if (poi.tags() != null) {
            for (String tag : poi.tags()) {
                if (tag == null || tag.isBlank()) {
                    continue;
                }
                String normalized = tag.trim().toLowerCase(Locale.ROOT);
                if (SOURCE_TAGS.contains(normalized)) {
                    continue;
                }
                return normalized;
            }
        }
        return "unknown";
    }

    private boolean isTooSimilar(List<Poi> candidate, List<CoursePlan> existing) {
        if (existing == null || existing.isEmpty()) {
            return false;
        }

        Set<String> candidateIds = candidate.stream()
            .map(poi -> poi.source() + ":" + poi.externalId())
            .collect(Collectors.toSet());

        int candidateSize = candidateIds.size();
        if (candidateSize == 0) {
            return false;
        }

        for (CoursePlan plan : existing) {
            if (plan == null || plan.stops() == null || plan.stops().isEmpty()) {
                continue;
            }
            Set<String> planIds = plan.stops().stream()
                .map(poi -> poi.source() + ":" + poi.externalId())
                .collect(Collectors.toSet());
            int overlap = 0;
            for (String id : candidateIds) {
                if (planIds.contains(id)) {
                    overlap++;
                }
            }
            double ratio = overlap / (double) Math.min(candidateSize, planIds.size());
            if (ratio >= SIMILARITY_THRESHOLD) {
                return true;
            }
        }

        return false;
    }
}
