package io.routepickapi.service.recommendation;

import io.routepickapi.dto.recommendation.CandidatePlace;
import io.routepickapi.dto.recommendation.CourseCandidate;
import io.routepickapi.dto.recommendation.GeoPoint;
import io.routepickapi.dto.recommendation.ValidationResult;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FinalRecommendationValidator {

    private static final double DUPLICATE_DISTANCE_KM = 0.2;
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

    private final PlaceRuleFilter placeRuleFilter;

    public List<CourseCandidate> validateCourses(
        List<CourseCandidate> courses,
        GeoPoint origin,
        GeoPoint destination,
        double maxDetourKm
    ) {
        if (courses == null || courses.isEmpty()) {
            return List.of();
        }

        List<CourseCandidate> valid = new ArrayList<>();
        for (CourseCandidate course : courses) {
            ValidationResult result = validate(course, origin, destination, maxDetourKm);
            if (result.valid()) {
                valid.add(course.withWarnings(result.warnings()));
            }
        }

        valid.sort((first, second) -> Double.compare(second.score(), first.score()));
        return valid;
    }

    private ValidationResult validate(
        CourseCandidate course,
        GeoPoint origin,
        GeoPoint destination,
        double maxDetourKm
    ) {
        if (course == null || course.stops() == null || course.stops().isEmpty()) {
            return new ValidationResult(false, List.of());
        }

        List<String> warnings = new ArrayList<>();
        double directDistance = GeoUtils.distanceKm(origin, destination);
        double detour = Math.max(0, course.totalDistanceKm() - directDistance);
        if (detour > maxDetourKm) {
            return new ValidationResult(false, List.of());
        }

        Set<String> nameSet = new HashSet<>();
        for (CandidatePlace stop : course.stops()) {
            if (!placeRuleFilter.filter(stop).passed()) {
                return new ValidationResult(false, List.of());
            }

            String key = normalize(stop.name());
            if (!nameSet.add(key)) {
                return new ValidationResult(false, List.of());
            }
        }

        if (hasNearDuplicates(course.stops())) {
            return new ValidationResult(false, List.of());
        }

        if (hasSameBrandSequence(course.stops())) {
            return new ValidationResult(false, List.of());
        }

        if (isSingleCategory(course.stops())) {
            warnings.add("low_category_diversity");
        }

        return new ValidationResult(true, warnings);
    }

    private boolean hasNearDuplicates(List<CandidatePlace> stops) {
        for (int first = 0; first < stops.size(); first++) {
            for (int second = first + 1; second < stops.size(); second++) {
                double distance = GeoUtils.distanceKm(
                    new GeoPoint(stops.get(first).x(), stops.get(first).y()),
                    new GeoPoint(stops.get(second).x(), stops.get(second).y())
                );
                if (distance <= DUPLICATE_DISTANCE_KM) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasSameBrandSequence(List<CandidatePlace> stops) {
        for (int index = 0; index < stops.size() - 1; index++) {
            String firstBrand = findBrand(stops.get(index).name());
            String secondBrand = findBrand(stops.get(index + 1).name());
            if (!firstBrand.isBlank() && firstBrand.equals(secondBrand)) {
                return true;
            }
        }
        return false;
    }

    private boolean isSingleCategory(List<CandidatePlace> stops) {
        Set<String> categories = new HashSet<>();
        for (CandidatePlace stop : stops) {
            categories.add(normalizeCategory(stop.categoryName()));
        }
        return categories.size() <= 1;
    }

    private String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.toLowerCase(Locale.ROOT).replace(" ", "");
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
