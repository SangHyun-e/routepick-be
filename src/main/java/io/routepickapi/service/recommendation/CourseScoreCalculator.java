package io.routepickapi.service.recommendation;

import io.routepickapi.dto.course.DriveRouteStyle;
import io.routepickapi.dto.recommendation.CandidatePlace;
import io.routepickapi.dto.recommendation.CandidateSource;
import io.routepickapi.dto.recommendation.CourseCandidate;
import io.routepickapi.dto.recommendation.DrivePreference;
import io.routepickapi.dto.recommendation.GeoPoint;
import io.routepickapi.dto.recommendation.ScoreDetail;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CourseScoreCalculator {

    private static final double ON_THE_WAY_WEIGHT = 0.32;
    private static final double SCENIC_WEIGHT = 0.2;
    private static final double THEME_WEIGHT = 0.15;
    private static final double DRIVE_WEIGHT = 0.08;
    private static final double DIVERSITY_WEIGHT = 0.1;
    private static final double TIME_WEIGHT = 0.08;
    private static final double STOP_COUNT_WEIGHT = 0.07;

    private static final double STOP_DIVERSITY_BONUS = 6.0;
    private static final double ROUTING_FALLBACK_PENALTY = 12.0;

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
        "휴게소",
        "viewpoint",
        "scenic",
        "lookout",
        "coast"
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

    private final RouteMetricsService routeMetricsService;

    public List<CourseCandidate> scoreCourses(
        List<CourseCandidate> courses,
        GeoPoint origin,
        GeoPoint destination,
        DrivePreference preference,
        int targetStops,
        RouteMetrics routeMetrics
    ) {
        if (courses == null || courses.isEmpty()) {
            return List.of();
        }

        List<CourseCandidate> scored = new ArrayList<>();
        for (CourseCandidate course : courses) {
            CourseCandidate scoredCourse = score(course, origin, destination, preference, targetStops, routeMetrics);
            if (scoredCourse != null) {
                scored.add(scoredCourse);
            }
        }
        scored.sort((first, second) -> Double.compare(second.score(), first.score()));
        return scored;
    }

    private CourseCandidate score(
        CourseCandidate course,
        GeoPoint origin,
        GeoPoint destination,
        DrivePreference preference,
        int targetStops,
        RouteMetrics routeMetrics
    ) {
        RouteMetrics safeMetrics = routeMetrics == null
            ? routeMetricsService.buildMetrics(origin, destination, 10)
            : routeMetrics;
        RouteMetricsService.RouteLegMetrics legMetrics = routeMetricsService.calculateMetrics(
            origin,
            destination,
            course.stops()
        );
        boolean routingFallback = !legMetrics.routingSuccess();
        if (legMetrics.distanceKm() <= 1.0 || legMetrics.durationMinutes() <= 0.0) {
            return null;
        }
        int estimatedMinutes = (int) Math.round(legMetrics.durationMinutes());
        CourseCandidate updatedCourse = course.withRouteMetrics(legMetrics.distanceKm(), estimatedMinutes);

        double baseDistance = safeMetrics.baseDistanceKm();
        double baseDurationMinutes = safeMetrics.baseDurationMinutes();
        double deviationKm = Math.max(0.0, updatedCourse.totalDistanceKm() - baseDistance);
        double delayMinutes = Math.max(0.0, estimatedMinutes - baseDurationMinutes);
        double onTheWayScore = calculateOnTheWayScore(deviationKm, safeMetrics.maxDetourKm());
        double scenicScore = calculateScenicScore(updatedCourse.stops());
        double themeMatchScore = calculateThemeMatch(updatedCourse, origin, destination, preference);
        double driveSuitability = calculateDriveSuitability(updatedCourse.stops());
        double categoryDiversity = calculateCategoryDiversity(updatedCourse.stops());
        double timeFit = calculateTimeFit(estimatedMinutes);
        double stopCountFit = calculateStopCountFit(updatedCourse.stops().size(), targetStops);
        double diversityBonus = calculateStopDiversityBonus(updatedCourse.stops());

        double deviationPenalty = calculateDeviationPenalty(deviationKm, safeMetrics.maxDetourKm());
        double delayPenalty = calculateDelayPenalty(delayMinutes, safeMetrics.maxDelayMinutes());
        double penalty = deviationPenalty + delayPenalty + calculatePenalty(updatedCourse.stops());
        List<String> penaltyReasons = collectPenaltyReasons(
            updatedCourse.stops(),
            deviationKm,
            delayMinutes,
            safeMetrics
        );
        if (routingFallback) {
            penalty += ROUTING_FALLBACK_PENALTY;
            penaltyReasons.add("routing_fallback");
        }

        double score = 100 * (ON_THE_WAY_WEIGHT * onTheWayScore + SCENIC_WEIGHT * scenicScore
            + THEME_WEIGHT * themeMatchScore + DRIVE_WEIGHT * driveSuitability
            + DIVERSITY_WEIGHT * categoryDiversity + TIME_WEIGHT * timeFit + STOP_COUNT_WEIGHT * stopCountFit)
            + diversityBonus - penalty;

        ScoreDetail scoreDetail = new ScoreDetail(
            onTheWayScore,
            scenicScore,
            themeMatchScore,
            driveSuitability,
            categoryDiversity,
            timeFit,
            stopCountFit,
            deviationPenalty,
            delayPenalty,
            penalty,
            penaltyReasons
        );

        return updatedCourse.withScore(score, scoreDetail);
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

    private double calculateThemeMatch(
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

    private List<String> collectPenaltyReasons(
        List<CandidatePlace> stops,
        double deviationKm,
        double delayMinutes,
        RouteMetrics routeMetrics
    ) {
        List<String> reasons = new ArrayList<>();
        if (deviationKm > routeMetrics.maxDetourKm() * 0.6) {
            reasons.add("route_deviation");
        }
        if (delayMinutes > routeMetrics.maxDelayMinutes() * 0.6) {
            reasons.add("destination_delay");
        }
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
        String value = (stop.name() + " " + stop.categoryName() + " "
            + String.join(" ", stop.safeTags())).toLowerCase(Locale.ROOT);
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

    private double calculateOnTheWayScore(double deviationKm, double maxDetourKm) {
        if (maxDetourKm <= 0) {
            return 0.5;
        }
        double ratio = deviationKm / maxDetourKm;
        return clamp(1.0 - ratio);
    }

    private double calculateScenicScore(List<CandidatePlace> stops) {
        if (stops.isEmpty()) {
            return 0.0;
        }
        double total = 0.0;
        for (CandidatePlace stop : stops) {
            total += scenicScore(stop);
        }
        return clamp(total / stops.size());
    }

    private double scenicScore(CandidatePlace stop) {
        double score = containsAny(stop, SCENIC_KEYWORDS) ? 0.7 : 0.3;
        CandidateSource source = stop.source();
        if (source == CandidateSource.OVERPASS) {
            score += 0.15;
        } else if (source == CandidateSource.TOURAPI) {
            score += 0.1;
        } else if (source == CandidateSource.KAKAO) {
            score -= 0.05;
        }
        return clamp(score);
    }

    private double calculateDeviationPenalty(double deviationKm, double maxDetourKm) {
        if (maxDetourKm <= 0) {
            return deviationKm * 2;
        }
        double ratio = deviationKm / maxDetourKm;
        return Math.max(0.0, ratio * 40.0);
    }

    private double calculateDelayPenalty(double delayMinutes, double maxDelayMinutes) {
        if (maxDelayMinutes <= 0) {
            return delayMinutes * 0.8;
        }
        double ratio = delayMinutes / maxDelayMinutes;
        return Math.max(0.0, ratio * 20.0);
    }

    private double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private double calculateStopDiversityBonus(List<CandidatePlace> stops) {
        if (stops == null || stops.isEmpty()) {
            return 0.0;
        }
        Set<String> categories = new HashSet<>();
        Set<CandidateSource> sources = new HashSet<>();
        for (CandidatePlace stop : stops) {
            categories.add(normalizeCategory(stop.categoryName()));
            if (stop.source() != null) {
                sources.add(stop.source());
            }
        }
        double categoryScore = categories.size() / (double) stops.size();
        double sourceScore = sources.isEmpty() ? 0.0 : sources.size() / (double) stops.size();
        double normalized = Math.min(1.0, (categoryScore + sourceScore) / 2.0);
        return normalized * STOP_DIVERSITY_BONUS;
    }
}
