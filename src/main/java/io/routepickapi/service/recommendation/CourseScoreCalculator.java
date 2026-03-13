package io.routepickapi.service.recommendation;

import io.routepickapi.dto.course.DriveRouteStyle;
import io.routepickapi.dto.recommendation.CandidatePlace;
import io.routepickapi.dto.recommendation.CourseCandidate;
import io.routepickapi.dto.recommendation.DrivePreference;
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
        DrivePreference preference,
        int targetStops
    ) {
        if (courses == null || courses.isEmpty()) {
            return List.of();
        }

        List<CourseCandidate> scored = new ArrayList<>();
        for (CourseCandidate course : courses) {
            scored.add(score(course, origin, destination, preference, targetStops));
        }
        scored.sort((first, second) -> Double.compare(second.score(), first.score()));
        return scored;
    }

    private CourseCandidate score(
        CourseCandidate course,
        GeoPoint origin,
        GeoPoint destination,
        DrivePreference preference,
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
        double preferenceFit = calculatePreferenceFit(course, origin, destination, preference);

        double penalty = calculatePenalty(course.stops());
        List<String> penaltyReasons = collectPenaltyReasons(course.stops());

        double score = 100 * (0.25 * driveSuitability + 0.2 * routeNaturalness
            + 0.15 * timeFit + 0.15 * categoryDiversity + 0.1 * stopCountFit
            + 0.15 * preferenceFit) - penalty;

        ScoreDetail scoreDetail = new ScoreDetail(
            driveSuitability,
            routeNaturalness,
            timeFit,
            categoryDiversity,
            stopCountFit,
            preferenceFit,
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

    private double calculatePreferenceFit(
        CourseCandidate course,
        GeoPoint origin,
        GeoPoint destination,
        DrivePreference preference
    ) {
        if (preference == null) {
            return 0.5;
        }

        List<String> moodKeywords = preference.moods().stream()
            .flatMap(mood -> mood.keywords().stream())
            .toList();
        List<String> stopKeywords = preference.stopTypes().stream()
            .flatMap(type -> type.keywords().stream())
            .toList();

        double moodFit = calculateKeywordFit(course.stops(), moodKeywords);
        double routeFit = calculateRouteStyleFit(course, origin, destination, preference.routeStyles());
        double stopFit = calculateKeywordFit(course.stops(), stopKeywords);

        return (moodFit + routeFit + stopFit) / 3.0;
    }

    private double calculateRouteStyleFit(
        CourseCandidate course,
        GeoPoint origin,
        GeoPoint destination,
        List<DriveRouteStyle> routeStyles
    ) {
        if (routeStyles == null || routeStyles.isEmpty()) {
            return 0.5;
        }

        List<DriveRouteStyle> activeStyles = routeStyles.stream()
            .filter(style -> style != DriveRouteStyle.NORMAL)
            .toList();
        if (activeStyles.isEmpty()) {
            return 0.5;
        }

        double bestScore = 0.0;
        for (DriveRouteStyle style : activeStyles) {
            double styleScore = style == DriveRouteStyle.WINDING
                ? calculateWindingScore(course, origin, destination)
                : calculateKeywordFit(course.stops(), style.keywords());
            bestScore = Math.max(bestScore, styleScore);
        }
        return bestScore;
    }

    private double calculateWindingScore(CourseCandidate course, GeoPoint origin, GeoPoint destination) {
        List<GeoPoint> points = new ArrayList<>();
        points.add(origin);
        for (CandidatePlace stop : course.stops()) {
            points.add(new GeoPoint(stop.x(), stop.y()));
        }
        points.add(destination);

        if (points.size() < 3) {
            return 0.0;
        }

        double totalAngle = 0.0;
        int angleCount = 0;
        for (int index = 1; index < points.size() - 1; index++) {
            GeoPoint previous = points.get(index - 1);
            GeoPoint current = points.get(index);
            GeoPoint next = points.get(index + 1);
            double angle = calculateTurnAngle(previous, current, next);
            if (!Double.isNaN(angle)) {
                totalAngle += angle;
                angleCount++;
            }
        }

        if (angleCount == 0) {
            return 0.0;
        }

        double averageAngle = totalAngle / angleCount;
        return Math.min(1.0, averageAngle / Math.PI);
    }

    private double calculateTurnAngle(GeoPoint previous, GeoPoint current, GeoPoint next) {
        double vectorOneX = current.x() - previous.x();
        double vectorOneY = current.y() - previous.y();
        double vectorTwoX = next.x() - current.x();
        double vectorTwoY = next.y() - current.y();

        double magnitudeOne = Math.hypot(vectorOneX, vectorOneY);
        double magnitudeTwo = Math.hypot(vectorTwoX, vectorTwoY);
        if (magnitudeOne == 0 || magnitudeTwo == 0) {
            return Double.NaN;
        }

        double cosine = (vectorOneX * vectorTwoX + vectorOneY * vectorTwoY)
            / (magnitudeOne * magnitudeTwo);
        double normalized = Math.max(-1.0, Math.min(1.0, cosine));
        return Math.acos(normalized);
    }

    private double calculateKeywordFit(List<CandidatePlace> stops, List<String> keywords) {
        if (keywords == null || keywords.isEmpty() || stops.isEmpty()) {
            return 0.5;
        }

        long matched = stops.stream()
            .filter(stop -> containsAny(stop, keywords))
            .count();
        return matched / (double) stops.size();
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
