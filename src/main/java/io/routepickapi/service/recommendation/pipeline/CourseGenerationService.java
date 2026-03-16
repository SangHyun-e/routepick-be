package io.routepickapi.service.recommendation.pipeline;

import io.routepickapi.domain.poi.Poi;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class CourseGenerationService {

    private static final int MIN_STOPS = 2;
    private static final int MAX_STOPS = 4;
    private static final int MAX_CANDIDATES = 30;
    private static final int DEFAULT_LIMIT = 40;

    public List<CoursePlan> generate(List<Poi> pois, int maxStops, int limit) {
        if (pois == null || pois.isEmpty()) {
            return List.of();
        }

        int safeMaxStops = Math.max(MIN_STOPS, Math.min(maxStops, MAX_STOPS));
        int safeLimit = limit > 0 ? limit : DEFAULT_LIMIT;

        List<Poi> candidates = pois.stream()
            .sorted(Comparator.comparingDouble(this::baseScore).reversed())
            .limit(MAX_CANDIDATES)
            .toList();

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
            results.add(new CoursePlan(List.copyOf(current)));
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
}
