package io.routepickapi.service.recommendation;

import io.routepickapi.dto.course.CourseTheme;
import io.routepickapi.dto.recommendation.CandidatePlace;
import io.routepickapi.dto.recommendation.CourseCandidate;
import io.routepickapi.dto.recommendation.GeoPoint;
import io.routepickapi.dto.recommendation.ScoreDetail;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class CourseScoreCalculator {

    private static final List<String> SCENIC_KEYWORDS = List.of(
        "전망",
        "해변",
        "해안",
        "공원",
        "호수",
        "강변",
        "야경",
        "드라이브",
        "산",
        "계곡",
        "자연",
        "전망대",
        "카페",
        "휴게소"
    );

    private static final List<String> BRAND_KEYWORDS = List.of(
        "스타벅스",
        "이디야",
        "투썸",
        "폴바셋",
        "탐앤탐스",
        "메가커피",
        "빽다방",
        "할리스"
    );

    public List<CourseCandidate> scoreCourses(
        List<CourseCandidate> courses,
        GeoPoint origin,
        GeoPoint destination,
        CourseTheme theme,
        int targetStops
    ) {
        if (courses == null || courses.isEmpty()) {
            return List.of();
        }

        List<CourseCandidate> scored = new ArrayList<>();
        for (CourseCandidate course : courses) {
            scored.add(score(course, origin, destination, theme, targetStops));
        }
        scored.sort((first, second) -> Double.compare(second.score(), first.score()));
        return scored;
    }

    private CourseCandidate score(
        CourseCandidate course,
        GeoPoint origin,
        GeoPoint destination,
        CourseTheme theme,
        int targetStops
    ) {
        double directDistance = GeoUtils.distanceKm(origin, destination);
        double routeNaturalness = directDistance == 0
            ? 0.5
            : Math.min(1.0, directDistance / course.totalDistanceKm());

        double driveSuitability = calculateDriveSuitability(course.stops());
        double categoryDiversity = calculateCategoryDiversity(course.stops());
        double timeFit = calculateTimeFit(course.estimatedMinutes());
        double stopCountFit = calculateStopCountFit(course.stops().size(), targetStops);

        double penalty = calculatePenalty(course.stops());
        List<String> penaltyReasons = collectPenaltyReasons(course.stops());

        double score = 100 * (0.3 * driveSuitability + 0.2 * routeNaturalness
            + 0.15 * timeFit + 0.2 * categoryDiversity + 0.15 * stopCountFit) - penalty;

        if (theme != null && matchesTheme(course.stops(), theme)) {
            score += 5;
        }

        ScoreDetail scoreDetail = new ScoreDetail(
            driveSuitability,
            routeNaturalness,
            timeFit,
            categoryDiversity,
            stopCountFit,
            penalty,
            penaltyReasons
        );

        return course.withScore(score, scoreDetail);
    }

    private double calculateDriveSuitability(List<CandidatePlace> stops) {
        long matched = stops.stream()
            .filter(stop -> containsAny(stop, SCENIC_KEYWORDS))
            .count();
        return stops.isEmpty() ? 0 : matched / (double) stops.size();
    }

    private double calculateCategoryDiversity(List<CandidatePlace> stops) {
        if (stops.isEmpty()) {
            return 0;
        }
        Set<String> categories = new HashSet<>();
        for (CandidatePlace stop : stops) {
            categories.add(normalizeCategory(stop.categoryName()));
        }
        return categories.size() / (double) stops.size();
    }

    private double calculateTimeFit(int minutes) {
        if (minutes >= 40 && minutes <= 120) {
            return 1.0;
        }
        if (minutes < 40) {
            return minutes / 40.0;
        }
        return Math.max(0.2, 1.0 - (minutes - 120) / 180.0);
    }

    private double calculateStopCountFit(int stopCount, int targetStops) {
        if (targetStops <= 0) {
            return 0.6;
        }
        int diff = Math.abs(stopCount - targetStops);
        if (diff == 0) {
            return 1.0;
        }
        if (diff == 1) {
            return 0.7;
        }
        return 0.4;
    }

    private double calculatePenalty(List<CandidatePlace> stops) {
        double penalty = 0;
        for (int index = 0; index < stops.size() - 1; index++) {
            if (sameCategory(stops.get(index), stops.get(index + 1))) {
                penalty += 4;
            }
            if (sameBrand(stops.get(index), stops.get(index + 1))) {
                penalty += 6;
            }
        }
        return penalty;
    }

    private List<String> collectPenaltyReasons(List<CandidatePlace> stops) {
        List<String> reasons = new ArrayList<>();
        for (int index = 0; index < stops.size() - 1; index++) {
            if (sameCategory(stops.get(index), stops.get(index + 1))) {
                reasons.add("same_category_sequence");
            }
            if (sameBrand(stops.get(index), stops.get(index + 1))) {
                reasons.add("same_brand_sequence");
            }
        }
        return reasons;
    }

    private boolean matchesTheme(List<CandidatePlace> stops, CourseTheme theme) {
        if (theme == null || theme.keywords() == null) {
            return false;
        }
        for (CandidatePlace stop : stops) {
            String value = (stop.name() + " " + stop.categoryName()).toLowerCase(Locale.ROOT);
            for (String keyword : theme.keywords()) {
                if (keyword != null && value.contains(keyword.toLowerCase(Locale.ROOT))) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean containsAny(CandidatePlace stop, List<String> keywords) {
        String value = (stop.name() + " " + stop.categoryName()).toLowerCase(Locale.ROOT);
        for (String keyword : keywords) {
            if (value.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private boolean sameCategory(CandidatePlace first, CandidatePlace second) {
        return normalizeCategory(first.categoryName()).equals(normalizeCategory(second.categoryName()));
    }

    private boolean sameBrand(CandidatePlace first, CandidatePlace second) {
        String firstBrand = findBrand(first.name());
        String secondBrand = findBrand(second.name());
        return !firstBrand.isBlank() && firstBrand.equals(secondBrand);
    }

    private String normalizeCategory(String categoryName) {
        if (categoryName == null) {
            return "";
        }
        String[] parts = categoryName.split(">");
        return parts.length == 0 ? categoryName.trim() : parts[0].trim();
    }

    private String findBrand(String name) {
        if (name == null) {
            return "";
        }
        for (String brand : BRAND_KEYWORDS) {
            if (name.contains(brand)) {
                return brand;
            }
        }
        return "";
    }
}
