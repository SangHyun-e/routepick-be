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
        return generate(pois, List.of(), maxStops, limit);
    }

    public List<CoursePlan> generate(List<Poi> pois, List<Poi> includeStops, int maxStops, int limit) {
        int safeLimit = limit > 0 ? limit : DEFAULT_LIMIT;
        int safeMaxStops = Math.max(MIN_STOPS, Math.min(maxStops, MAX_STOPS));
        List<Poi> fixedStops = normalizeFixedStops(includeStops);
        if (!fixedStops.isEmpty()) {
            safeMaxStops = Math.max(safeMaxStops, Math.min(MAX_STOPS, fixedStops.size()));
        }

        List<Poi> candidates = pois == null ? List.of() : buildDiverseCandidates(pois);
        if (fixedStops.isEmpty()) {
            if (candidates.isEmpty()) {
                return List.of();
            }
            return generateWithCandidates(candidates, safeMaxStops, safeLimit);
        }

        return generateWithFixedStops(candidates, fixedStops, safeMaxStops, safeLimit);
    }

    private List<CoursePlan> generateWithCandidates(List<Poi> candidates, int safeMaxStops, int safeLimit) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        List<CoursePlan> plans = new ArrayList<>();
        Map<Integer, Integer> generatedByStops = new LinkedHashMap<>();
        Map<String, Integer> firstStopCounts = new LinkedHashMap<>();

        for (int stopCount = safeMaxStops; stopCount >= MIN_STOPS; stopCount--) {
            int before = plans.size();
            buildDiverseCombinations(candidates, stopCount, plans, safeLimit, firstStopCounts);
            int added = plans.size() - before;
            generatedByStops.put(stopCount, added);
            if (plans.size() >= safeLimit) {
                break;
            }
        }

        int uniqueFirstStops = (int) plans.stream()
            .map(plan -> firstStopKey(plan.stops().getFirst()))
            .filter(key -> key != null && !key.isBlank())
            .distinct()
            .count();
        log.info(
            "코스 조합 생성 완료 - count={}, uniqueFirstStops={}, stop2={}, stop3={}, requestedMaxStops={}",
            plans.size(),
            uniqueFirstStops,
            generatedByStops.getOrDefault(2, 0),
            generatedByStops.getOrDefault(3, 0),
            safeMaxStops
        );
        return plans;
    }

    private List<CoursePlan> generateWithFixedStops(
        List<Poi> candidates,
        List<Poi> fixedStops,
        int safeMaxStops,
        int safeLimit
    ) {
        List<CoursePlan> plans = new ArrayList<>();
        Set<String> fixedKeys = fixedStops.stream()
            .map(this::stopKey)
            .collect(Collectors.toSet());
        List<Poi> filteredCandidates = candidates == null
            ? List.of()
            : candidates.stream()
                .filter(poi -> !fixedKeys.contains(stopKey(poi)))
                .toList();

        int fixedCount = fixedStops.size();
        for (int stopCount = safeMaxStops; stopCount >= MIN_STOPS; stopCount--) {
            if (stopCount < fixedCount) {
                continue;
            }
            int requiredAdditional = stopCount - fixedCount;
            if (requiredAdditional == 0) {
                List<Poi> combined = List.copyOf(fixedStops);
                if (!isTooSimilar(combined, plans)) {
                    plans.add(new CoursePlan(combined));
                }
            } else if (!filteredCandidates.isEmpty()) {
                buildCombinationsWithFixedStops(
                    filteredCandidates,
                    requiredAdditional,
                    0,
                    new ArrayList<>(),
                    fixedStops,
                    plans,
                    safeLimit
                );
            }

            if (plans.size() >= safeLimit) {
                break;
            }
        }

        log.info(
            "고정 경유지 포함 코스 생성 완료 - count={}, fixedStops={}, maxStops={}",
            plans.size(),
            fixedStops.size(),
            safeMaxStops
        );
        return plans;
    }

    private void buildDiverseCombinations(
        List<Poi> candidates,
        int targetSize,
        List<CoursePlan> results,
        int limit,
        Map<String, Integer> firstStopCounts
    ) {
        if (results.size() >= limit) {
            return;
        }

        List<Poi> firstStops = buildFirstStopCandidates(candidates);
        int remainingLimit = Math.max(1, limit - results.size());
        int perFirstStopLimit = Math.max(1, remainingLimit / Math.max(1, firstStops.size()));

        for (Poi firstStop : firstStops) {
            if (results.size() >= limit) {
                return;
            }
            String firstKey = firstStopKey(firstStop);
            List<Poi> remaining = candidates.stream()
                .filter(poi -> !firstKey.equals(firstStopKey(poi)))
                .toList();
            List<Poi> current = new ArrayList<>();
            current.add(firstStop);
            buildCombinationsWithCap(
                remaining,
                targetSize,
                0,
                current,
                results,
                limit,
                firstKey,
                firstStopCounts,
                perFirstStopLimit
            );
        }

        if (results.size() < limit) {
            buildCombinations(candidates, targetSize, 0, new ArrayList<>(), results, limit);
        }
    }

    private void buildCombinationsWithCap(
        List<Poi> candidates,
        int targetSize,
        int startIndex,
        List<Poi> current,
        List<CoursePlan> results,
        int limit,
        String firstStopKey,
        Map<String, Integer> firstStopCounts,
        int perFirstStopLimit
    ) {
        if (results.size() >= limit) {
            return;
        }
        int currentCount = firstStopCounts.getOrDefault(firstStopKey, 0);
        if (currentCount >= perFirstStopLimit) {
            return;
        }
        if (current.size() == targetSize) {
            if (!isTooSimilar(current, results)) {
                results.add(new CoursePlan(List.copyOf(current)));
                firstStopCounts.put(firstStopKey, currentCount + 1);
            }
            return;
        }

        for (int index = startIndex; index < candidates.size(); index++) {
            if (results.size() >= limit) {
                return;
            }
            current.add(candidates.get(index));
            buildCombinationsWithCap(
                candidates,
                targetSize,
                index + 1,
                current,
                results,
                limit,
                firstStopKey,
                firstStopCounts,
                perFirstStopLimit
            );
            current.remove(current.size() - 1);
        }
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

    private void buildCombinationsWithFixedStops(
        List<Poi> candidates,
        int targetSize,
        int startIndex,
        List<Poi> current,
        List<Poi> fixedStops,
        List<CoursePlan> results,
        int limit
    ) {
        if (results.size() >= limit) {
            return;
        }
        if (current.size() == targetSize) {
            List<Poi> combined = new ArrayList<>(fixedStops);
            combined.addAll(current);
            if (!isTooSimilar(combined, results)) {
                results.add(new CoursePlan(List.copyOf(combined)));
            }
            return;
        }

        for (int index = startIndex; index < candidates.size(); index++) {
            if (results.size() >= limit) {
                return;
            }
            current.add(candidates.get(index));
            buildCombinationsWithFixedStops(
                candidates,
                targetSize,
                index + 1,
                current,
                fixedStops,
                results,
                limit
            );
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

    private List<Poi> buildFirstStopCandidates(List<Poi> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        List<Poi> unique = new ArrayList<>();
        Set<String> seen = new java.util.LinkedHashSet<>();
        for (Poi poi : candidates) {
            String key = firstStopKey(poi);
            if (key.isBlank() || !seen.add(key)) {
                continue;
            }
            unique.add(poi);
        }
        return unique;
    }

    private List<Poi> normalizeFixedStops(List<Poi> includeStops) {
        if (includeStops == null || includeStops.isEmpty()) {
            return List.of();
        }
        List<Poi> normalized = new ArrayList<>();
        Set<String> seen = new java.util.LinkedHashSet<>();
        for (Poi poi : includeStops) {
            if (poi == null) {
                continue;
            }
            String key = stopKey(poi);
            if (!seen.add(key)) {
                continue;
            }
            normalized.add(poi);
        }
        return normalized;
    }

    private String stopKey(Poi poi) {
        if (poi == null) {
            return "";
        }
        return poi.source() + ":" + poi.externalId();
    }

    private String firstStopKey(Poi poi) {
        if (poi == null) {
            return "";
        }
        String source = poi.source() == null ? "" : poi.source().trim().toLowerCase(Locale.ROOT);
        String externalId = poi.externalId() == null ? "" : poi.externalId().trim();
        if (!externalId.isBlank()) {
            return source + ":" + externalId;
        }
        String name = poi.name() == null ? "" : poi.name().trim().toLowerCase(Locale.ROOT);
        long latKey = Math.round(poi.lat() * 10000);
        long lngKey = Math.round(poi.lng() * 10000);
        return name + ":" + latKey + ":" + lngKey;
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
