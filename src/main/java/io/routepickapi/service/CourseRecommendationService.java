package io.routepickapi.service;

import io.routepickapi.common.error.CustomException;
import io.routepickapi.common.error.ErrorType;
import io.routepickapi.dto.course.CourseRecommendationRequest;
import io.routepickapi.dto.course.CourseRecommendationResponse;
import io.routepickapi.dto.course.CourseRecommendationConditionStatus;
import io.routepickapi.dto.course.CourseRecommendationRelaxation;
import io.routepickapi.dto.course.CourseStopResponse;
import io.routepickapi.dto.course.DriveMood;
import io.routepickapi.dto.course.DriveRouteStyle;
import io.routepickapi.dto.course.DriveStopType;
import io.routepickapi.dto.place.KakaoPlaceSearchResponse;
import io.routepickapi.dto.place.KakaoPlaceSearchResponse.KakaoPlaceDocument;
import io.routepickapi.dto.recommendation.CandidatePlace;
import io.routepickapi.dto.recommendation.CourseCandidate;
import io.routepickapi.dto.recommendation.DrivePreference;
import io.routepickapi.dto.recommendation.GeoPoint;
import io.routepickapi.service.recommendation.CandidatePlaceCollector;
import io.routepickapi.service.recommendation.CourseCandidateBuilder;
import io.routepickapi.service.recommendation.CourseScoreCalculator;
import io.routepickapi.service.recommendation.FinalRecommendationValidator;
import io.routepickapi.service.recommendation.PlaceDeduplicator;
import io.routepickapi.service.recommendation.RecommendationFallbackPolicy;
import io.routepickapi.service.recommendation.CandidateSearchOption;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CourseRecommendationService {

    private static final int DEFAULT_MAX_STOPS = 3;
    private static final int MIN_STOPS = 2;
    private static final int MAX_STOPS = 4;
    private static final int MIN_COURSE_COUNT = 3;
    private static final int EXPANDED_SEARCH_RADIUS_METERS = 12000;
    private static final String RELAXATION_MESSAGE =
        "선택한 조건에 맞는 코스가 부족해 일부 조건을 완화했습니다.";

    private final KakaoLocalService kakaoLocalService;
    private final CandidatePlaceCollector candidatePlaceCollector;
    private final PlaceDeduplicator placeDeduplicator;
    private final CourseCandidateBuilder courseCandidateBuilder;
    private final CourseScoreCalculator courseScoreCalculator;
    private final FinalRecommendationValidator finalRecommendationValidator;
    private final RecommendationFallbackPolicy recommendationFallbackPolicy;

    public CourseRecommendationResponse recommend(CourseRecommendationRequest request) {
        if (request == null) {
            throw new CustomException(ErrorType.COMMON_INVALID_INPUT, "요청값이 비어있습니다.");
        }

        DrivePreference preference = resolvePreferences(
            request.moods(),
            request.stopTypes(),
            request.routeStyles(),
            request.autoRecommend()
        );
        int maxStops = sanitizeMaxStops(request.maxStops());
        double maxDetourKm = sanitizeMaxDetourKm(request.maxDetourKm());

        GeoPoint origin = resolvePoint(request.origin());
        GeoPoint destination = resolvePoint(request.destination());

        RecommendationOutcome outcome = recommendWithRelaxation(
            request,
            preference,
            origin,
            destination,
            maxStops,
            maxDetourKm
        );

        CourseCandidate bestCourse = outcome.bestCourse();
        List<CourseStopResponse> stops = bestCourse == null
            ? List.of()
            : bestCourse.stops().stream().map(this::toStopResponse).toList();

        String routeSummary = buildRouteSummary(request.origin(), request.destination(), stops);
        String explanation = buildExplanation(preference, stops);
        CourseRecommendationRelaxation relaxation = buildRelaxationSummary(
            request,
            outcome.relaxationState()
        );

        log.info("추천 코스 생성 완료 - stops={}", stops.size());
        return new CourseRecommendationResponse(stops, routeSummary, explanation, relaxation);
    }

    public List<CourseStopResponse> recommendCandidates(
        String origin,
        String destination,
        List<String> moods,
        List<String> stopTypes,
        List<String> routeStyles,
        Boolean autoRecommend,
        int limit
    ) {
        if (limit <= 0) {
            return List.of();
        }

        DrivePreference preference = resolvePreferences(moods, stopTypes, routeStyles, autoRecommend);
        GeoPoint originPoint = resolvePoint(origin);
        GeoPoint destinationPoint = resolvePoint(destination);
        List<CandidatePlace> candidates = candidatePlaceCollector.collectCandidates(
            originPoint,
            destinationPoint,
            preference
        );
        List<CandidatePlace> deduplicated = placeDeduplicator.deduplicate(candidates);

        return deduplicated.stream()
            .limit(limit)
            .map(this::toStopResponse)
            .toList();
    }

    private RecommendationOutcome recommendWithRelaxation(
        CourseRecommendationRequest request,
        DrivePreference preference,
        GeoPoint origin,
        GeoPoint destination,
        int maxStops,
        double maxDetourKm
    ) {
        ConditionRequirement requirement = buildConditionRequirement(request, preference);
        CandidateSearchOption searchOption = CandidateSearchOption.defaultOption()
            .withStopTypeFilter(requirement.requireStopType());
        RecommendationRun run = buildRecommendationRun(
            origin,
            destination,
            preference,
            maxStops,
            maxDetourKm,
            searchOption
        );

        RelaxationState relaxationState = new RelaxationState(
            false,
            false,
            false,
            false,
            searchOption.searchRadiusMeters()
        );

        List<CourseCandidate> filtered = filterByConditions(
            run.courses(),
            origin,
            destination,
            preference,
            requirement
        );

        if (filtered.size() < MIN_COURSE_COUNT && requirement.requireRouteStyle()) {
            requirement = requirement.withoutRouteStyle();
            relaxationState = relaxationState.withRouteStyleRelaxed();
            filtered = filterByConditions(run.courses(), origin, destination, preference, requirement);
        }

        if (filtered.size() < MIN_COURSE_COUNT && requirement.requireStopType()) {
            requirement = requirement.withoutStopType();
            relaxationState = relaxationState.withStopTypeRelaxed();
            searchOption = searchOption.withStopTypeFilter(false);
            run = buildRecommendationRun(
                origin,
                destination,
                preference,
                maxStops,
                maxDetourKm,
                searchOption
            );
            filtered = filterByConditions(run.courses(), origin, destination, preference, requirement);
        }

        if (filtered.size() < MIN_COURSE_COUNT && requirement.requireMood()) {
            requirement = requirement.withoutMood();
            relaxationState = relaxationState.withMoodRelaxed();
            filtered = filterByConditions(run.courses(), origin, destination, preference, requirement);
        }

        if (filtered.size() < MIN_COURSE_COUNT) {
            relaxationState = relaxationState.withRadiusRelaxed(EXPANDED_SEARCH_RADIUS_METERS);
            searchOption = searchOption.withSearchRadiusMeters(EXPANDED_SEARCH_RADIUS_METERS);
            run = buildRecommendationRun(
                origin,
                destination,
                preference,
                maxStops,
                maxDetourKm,
                searchOption
            );
            filtered = filterByConditions(run.courses(), origin, destination, preference, requirement);
        }

        if (filtered.isEmpty()) {
            filtered = applyFallback(origin, destination, run.candidates(), preference, maxStops);
        }

        CourseCandidate bestCourse = selectBestCourse(filtered, maxStops);
        return new RecommendationOutcome(bestCourse, relaxationState);
    }

    private RecommendationRun buildRecommendationRun(
        GeoPoint origin,
        GeoPoint destination,
        DrivePreference preference,
        int maxStops,
        double maxDetourKm,
        CandidateSearchOption searchOption
    ) {
        List<CandidatePlace> candidates = candidatePlaceCollector.collectCandidates(
            origin,
            destination,
            preference,
            searchOption
        );
        List<CandidatePlace> deduplicated = placeDeduplicator.deduplicate(candidates);
        List<CourseCandidate> courses = courseCandidateBuilder.buildCourses(
            origin,
            destination,
            deduplicated,
            MIN_STOPS,
            maxStops
        );

        List<CourseCandidate> scored = courseScoreCalculator.scoreCourses(
            courses,
            origin,
            destination,
            preference,
            maxStops
        );
        List<CourseCandidate> validated = finalRecommendationValidator.validateCourses(
            scored,
            origin,
            destination,
            maxDetourKm
        );
        return new RecommendationRun(deduplicated, validated);
    }

    private List<CourseCandidate> filterByConditions(
        List<CourseCandidate> courses,
        GeoPoint origin,
        GeoPoint destination,
        DrivePreference preference,
        ConditionRequirement requirement
    ) {
        if (courses == null || courses.isEmpty()) {
            return List.of();
        }

        return courses.stream()
            .filter(course -> !requirement.requireMood()
                || matchesMoods(course, preference.moods()))
            .filter(course -> !requirement.requireStopType()
                || matchesStopTypes(course, preference.stopTypes()))
            .filter(course -> !requirement.requireRouteStyle()
                || matchesRouteStyles(course, origin, destination, preference.routeStyles()))
            .toList();
    }

    private List<CourseCandidate> applyFallback(
        GeoPoint origin,
        GeoPoint destination,
        List<CandidatePlace> candidates,
        DrivePreference preference,
        int maxStops
    ) {
        List<CandidatePlace> baseCandidates = candidates;
        if (baseCandidates == null || baseCandidates.isEmpty()) {
            CandidateSearchOption fallbackOption = CandidateSearchOption.defaultOption()
                .withSearchRadiusMeters(EXPANDED_SEARCH_RADIUS_METERS)
                .withStopTypeFilter(false);
            baseCandidates = candidatePlaceCollector.collectCandidates(
                origin,
                destination,
                preference,
                fallbackOption
            );
            baseCandidates = placeDeduplicator.deduplicate(baseCandidates);
        }

        List<CourseCandidate> fallback = recommendationFallbackPolicy.fallback(
            baseCandidates,
            origin,
            destination,
            MIN_STOPS
        );
        return courseScoreCalculator.scoreCourses(
            fallback,
            origin,
            destination,
            preference,
            maxStops
        );
    }

    private ConditionRequirement buildConditionRequirement(
        CourseRecommendationRequest request,
        DrivePreference preference
    ) {
        if (preference.autoRecommend()) {
            return new ConditionRequirement(false, false, false);
        }

        boolean requireMood = request.moods() != null && !request.moods().isEmpty();
        boolean requireStopType = request.stopTypes() != null && !request.stopTypes().isEmpty();
        boolean requireRouteStyle = request.routeStyles() != null && !request.routeStyles().isEmpty()
            && preference.routeStyles().stream().anyMatch(style -> style != DriveRouteStyle.NORMAL);
        return new ConditionRequirement(requireRouteStyle, requireStopType, requireMood);
    }

    private CourseRecommendationRelaxation buildRelaxationSummary(
        CourseRecommendationRequest request,
        RelaxationState relaxationState
    ) {
        List<CourseRecommendationConditionStatus> conditions = new ArrayList<>();

        List<DriveMood> moods = DriveMood.fromLabels(request.moods());
        if (!moods.isEmpty()) {
            conditions.add(new CourseRecommendationConditionStatus(
                "분위기",
                joinLabels(moods.stream().map(DriveMood::label).toList()),
                relaxationState.moodRelaxed()
            ));
        }

        List<DriveStopType> stopTypes = DriveStopType.fromLabels(request.stopTypes());
        if (!stopTypes.isEmpty()) {
            conditions.add(new CourseRecommendationConditionStatus(
                "들를 곳",
                joinLabels(stopTypes.stream().map(DriveStopType::label).toList()),
                relaxationState.stopTypeRelaxed()
            ));
        }

        List<DriveRouteStyle> routeStyles = DriveRouteStyle.fromLabels(request.routeStyles());
        if (!routeStyles.isEmpty()) {
            conditions.add(new CourseRecommendationConditionStatus(
                "길 스타일",
                joinLabels(routeStyles.stream().map(DriveRouteStyle::label).toList()),
                relaxationState.routeStyleRelaxed()
            ));
        }

        boolean relaxed = relaxationState.relaxed();
        String message = relaxed ? RELAXATION_MESSAGE : "";
        return new CourseRecommendationRelaxation(
            relaxed,
            message,
            conditions,
            relaxationState.searchRadiusMeters(),
            relaxationState.radiusRelaxed()
        );
    }

    private String joinLabels(List<String> labels) {
        return labels.stream()
            .filter(Objects::nonNull)
            .filter(value -> !value.isBlank())
            .distinct()
            .reduce((first, second) -> first + ", " + second)
            .orElse("");
    }

    private boolean matchesMoods(CourseCandidate course, List<DriveMood> moods) {
        if (moods == null || moods.isEmpty()) {
            return true;
        }

        List<String> keywords = moods.stream()
            .flatMap(mood -> mood.keywords().stream())
            .toList();
        return course.stops().stream().anyMatch(stop -> containsAny(stop, keywords));
    }

    private boolean matchesStopTypes(CourseCandidate course, List<DriveStopType> stopTypes) {
        if (stopTypes == null || stopTypes.isEmpty()) {
            return true;
        }
        return course.stops().stream().anyMatch(stop -> matchesStopType(stop, stopTypes));
    }

    private boolean matchesStopType(CandidatePlace candidate, List<DriveStopType> stopTypes) {
        for (DriveStopType stopType : stopTypes) {
            if (matchesStopType(candidate, stopType)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesStopType(CandidatePlace candidate, DriveStopType stopType) {
        if (stopType == null) {
            return false;
        }

        String value = concatPlace(candidate);
        if (!stopType.requiredGroupCodes().isEmpty()) {
            String groupCode = candidate.categoryGroupCode();
            if (groupCode == null || !stopType.requiredGroupCodes().contains(groupCode)) {
                return false;
            }
        }

        if (!stopType.blockedKeywords().isEmpty()
            && stopType.blockedKeywords().stream().anyMatch(value::contains)) {
            return false;
        }

        if (stopType.keywords().isEmpty()) {
            return true;
        }

        return stopType.keywords().stream().anyMatch(value::contains);
    }

    private boolean matchesRouteStyles(
        CourseCandidate course,
        GeoPoint origin,
        GeoPoint destination,
        List<DriveRouteStyle> routeStyles
    ) {
        if (routeStyles == null || routeStyles.isEmpty()) {
            return true;
        }

        List<DriveRouteStyle> activeStyles = routeStyles.stream()
            .filter(style -> style != DriveRouteStyle.NORMAL)
            .toList();
        if (activeStyles.isEmpty()) {
            return true;
        }

        for (DriveRouteStyle style : activeStyles) {
            if (style == DriveRouteStyle.WINDING) {
                if (calculateWindingScore(course, origin, destination) >= 0.35) {
                    return true;
                }
                continue;
            }

            boolean blocked = style.blockedKeywords().stream()
                .anyMatch(keyword -> course.stops().stream()
                    .map(this::concatPlace)
                    .anyMatch(value -> value.contains(keyword)));
            if (blocked) {
                continue;
            }

            boolean matched = course.stops().stream()
                .anyMatch(stop -> containsAny(stop, style.keywords()));
            if (matched) {
                return true;
            }
        }

        return false;
    }

    private boolean containsAny(CandidatePlace candidate, List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return false;
        }
        String value = concatPlace(candidate);
        return keywords.stream().anyMatch(value::contains);
    }

    private String concatPlace(CandidatePlace candidate) {
        return String.join(" ",
            safeLower(candidate.name()),
            safeLower(candidate.categoryName()),
            safeLower(candidate.categoryGroupName())
        );
    }

    private String safeLower(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private double calculateWindingScore(
        CourseCandidate course,
        GeoPoint origin,
        GeoPoint destination
    ) {
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

    private int sanitizeMaxStops(Integer maxStops) {
        if (maxStops == null) {
            return DEFAULT_MAX_STOPS;
        }
        return Math.max(MIN_STOPS, Math.min(MAX_STOPS, maxStops));
    }

    private double sanitizeMaxDetourKm(Integer maxDetourKm) {
        if (maxDetourKm == null) {
            return 15;
        }
        return Math.max(5, Math.min(50, maxDetourKm));
    }

    private GeoPoint resolvePoint(String query) {
        KakaoPlaceSearchResponse response = kakaoLocalService.searchKeyword(query, 1, 1);
        if (response == null || response.documents() == null || response.documents().isEmpty()) {
            throw new CustomException(ErrorType.COMMON_NOT_FOUND, "좌표를 찾을 수 없습니다.");
        }

        KakaoPlaceDocument document = response.documents().getFirst();
        GeoPoint point = parsePoint(document.x(), document.y());
        if (point == null) {
            throw new CustomException(ErrorType.COMMON_NOT_FOUND, "좌표를 찾을 수 없습니다.");
        }

        return point;
    }

    private GeoPoint parsePoint(String x, String y) {
        if (x == null || y == null) {
            return null;
        }

        try {
            return new GeoPoint(Double.parseDouble(x), Double.parseDouble(y));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private CourseStopResponse toStopResponse(CandidatePlace candidate) {
        return new CourseStopResponse(
            candidate.name(),
            candidate.address(),
            candidate.x(),
            candidate.y(),
            candidate.categoryName()
        );
    }

    private String buildRouteSummary(
        String origin,
        String destination,
        List<CourseStopResponse> stops
    ) {
        String stopNames = stops.stream()
            .map(CourseStopResponse::name)
            .reduce((first, second) -> first + " → " + second)
            .orElse("");

        if (stopNames.isBlank()) {
            return String.format("%s → %s", origin, destination);
        }

        return String.format("%s → %s → %s", origin, stopNames, destination);
    }

    private String buildExplanation(DrivePreference preference, List<CourseStopResponse> stops) {
        if (stops.isEmpty()) {
            return "조건에 맞는 코스가 부족해 기본 경로를 먼저 안내합니다.";
        }

        String preferenceSummary = buildPreferenceSummary(preference);
        List<String> stopNames = stops.stream()
            .map(CourseStopResponse::name)
            .toList();
        String summary = String.join(", ", stopNames);
        if (preferenceSummary.isBlank()) {
            return String.format(Locale.KOREAN,
                "추천 경유지는 %s입니다.",
                summary
            );
        }
        return String.format(Locale.KOREAN,
            "%s 조건으로 코스를 구성했습니다. 추천 경유지는 %s입니다.",
            preferenceSummary,
            summary
        );
    }

    private CourseCandidate selectBestCourse(List<CourseCandidate> courses, int targetStops) {
        if (courses == null || courses.isEmpty()) {
            return null;
        }

        List<CourseCandidate> exact = courses.stream()
            .filter(course -> course.stops().size() == targetStops)
            .toList();

        if (!exact.isEmpty()) {
            return exact.getFirst();
        }

        return courses.getFirst();
    }

    private DrivePreference resolvePreferences(
        List<String> moodLabels,
        List<String> stopTypeLabels,
        List<String> routeStyleLabels,
        Boolean autoRecommend
    ) {
        List<DriveMood> moods = DriveMood.fromLabels(moodLabels);
        List<DriveStopType> stopTypes = DriveStopType.fromLabels(stopTypeLabels);
        List<DriveRouteStyle> routeStyles = DriveRouteStyle.fromLabels(routeStyleLabels);
        boolean isAutoRecommend = Boolean.TRUE.equals(autoRecommend)
            || (moods.isEmpty() && stopTypes.isEmpty() && routeStyles.isEmpty());

        if (isAutoRecommend) {
            moods = defaultMoods();
            stopTypes = List.of(DriveStopType.MOOD_CAFE, DriveStopType.VIEWPOINT, DriveStopType.WALK);
            routeStyles = List.of(DriveRouteStyle.NORMAL);
        } else {
            if (moods.isEmpty()) {
                moods = List.of(DriveMood.HEALING);
            }
            if (stopTypes.isEmpty()) {
                stopTypes = List.of(DriveStopType.MOOD_CAFE, DriveStopType.VIEWPOINT);
            }
            if (routeStyles.isEmpty()) {
                routeStyles = List.of(DriveRouteStyle.NORMAL);
            }
        }

        return new DrivePreference(moods, stopTypes, routeStyles, isAutoRecommend);
    }

    private List<DriveMood> defaultMoods() {
        LocalTime now = LocalTime.now();
        if (now.isAfter(LocalTime.of(18, 0)) || now.isBefore(LocalTime.of(5, 0))) {
            return List.of(DriveMood.NIGHT_VIEW);
        }
        return List.of(DriveMood.HEALING);
    }

    private String buildPreferenceSummary(DrivePreference preference) {
        if (preference == null) {
            return "";
        }
        if (preference.autoRecommend()) {
            return "서비스 추천";
        }

        String moods = preference.moods().stream()
            .map(DriveMood::label)
            .filter(Objects::nonNull)
            .distinct()
            .reduce((first, second) -> first + ", " + second)
            .orElse("");
        String stops = preference.stopTypes().stream()
            .map(DriveStopType::label)
            .filter(Objects::nonNull)
            .distinct()
            .reduce((first, second) -> first + ", " + second)
            .orElse("");
        String routes = preference.routeStyles().stream()
            .map(DriveRouteStyle::label)
            .filter(Objects::nonNull)
            .distinct()
            .reduce((first, second) -> first + ", " + second)
            .orElse("");

        StringBuilder summary = new StringBuilder();
        if (!moods.isBlank()) {
            summary.append("분위기: ").append(moods);
        }
        if (!stops.isBlank()) {
            if (summary.length() > 0) {
                summary.append(" | ");
            }
            summary.append("들를 곳: ").append(stops);
        }
        if (!routes.isBlank()) {
            if (summary.length() > 0) {
                summary.append(" | ");
            }
            summary.append("길 스타일: ").append(routes);
        }
        return summary.toString();
    }

    private record RecommendationOutcome(
        CourseCandidate bestCourse,
        RelaxationState relaxationState
    ) {
    }

    private record RecommendationRun(
        List<CandidatePlace> candidates,
        List<CourseCandidate> courses
    ) {
    }

    private record ConditionRequirement(
        boolean requireRouteStyle,
        boolean requireStopType,
        boolean requireMood
    ) {
        ConditionRequirement withoutRouteStyle() {
            return new ConditionRequirement(false, requireStopType, requireMood);
        }

        ConditionRequirement withoutStopType() {
            return new ConditionRequirement(requireRouteStyle, false, requireMood);
        }

        ConditionRequirement withoutMood() {
            return new ConditionRequirement(requireRouteStyle, requireStopType, false);
        }
    }

    private record RelaxationState(
        boolean routeStyleRelaxed,
        boolean stopTypeRelaxed,
        boolean moodRelaxed,
        boolean radiusRelaxed,
        int searchRadiusMeters
    ) {
        RelaxationState withRouteStyleRelaxed() {
            return new RelaxationState(true, stopTypeRelaxed, moodRelaxed, radiusRelaxed, searchRadiusMeters);
        }

        RelaxationState withStopTypeRelaxed() {
            return new RelaxationState(routeStyleRelaxed, true, moodRelaxed, radiusRelaxed, searchRadiusMeters);
        }

        RelaxationState withMoodRelaxed() {
            return new RelaxationState(routeStyleRelaxed, stopTypeRelaxed, true, radiusRelaxed, searchRadiusMeters);
        }

        RelaxationState withRadiusRelaxed(int radiusMeters) {
            return new RelaxationState(routeStyleRelaxed, stopTypeRelaxed, moodRelaxed, true, radiusMeters);
        }

        boolean relaxed() {
            return routeStyleRelaxed || stopTypeRelaxed || moodRelaxed || radiusRelaxed;
        }
    }
}
