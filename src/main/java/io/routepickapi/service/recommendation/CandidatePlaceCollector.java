package io.routepickapi.service.recommendation;

import io.routepickapi.dto.course.DriveStopType;
import io.routepickapi.dto.place.KakaoPlaceSearchResponse;
import io.routepickapi.dto.place.KakaoPlaceSearchResponse.KakaoPlaceDocument;
import io.routepickapi.dto.recommendation.CandidatePlace;
import io.routepickapi.dto.recommendation.CandidateSource;
import io.routepickapi.dto.recommendation.DrivePreference;
import io.routepickapi.dto.recommendation.FilterDecision;
import io.routepickapi.dto.recommendation.GeoPoint;
import io.routepickapi.infrastructure.client.overpass.OverpassClient;
import io.routepickapi.infrastructure.client.overpass.dto.OverpassElement;
import io.routepickapi.infrastructure.client.overpass.dto.OverpassResponse;
import io.routepickapi.infrastructure.client.tour.TourApiClient;
import io.routepickapi.infrastructure.client.tour.dto.TourItem;
import io.routepickapi.service.KakaoLocalService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CandidatePlaceCollector {

    private static final int MIDPOINT_COUNT = 1;
    private static final int SEARCH_PAGE = 1;
    private static final int SEARCH_SIZE = 15;
    private static final int TOUR_PAGE = 1;
    private static final int TOUR_SIZE = 70;
    private static final int OVERPASS_LIMIT = 40;
    private static final int DEFAULT_KAKAO_CAP = 10;
    private static final int DEFAULT_TOUR_CAP = 20;
    private static final int DEFAULT_OVERPASS_CAP = 0;
    private static final double MAX_ROUTE_DEVIATION_KM = 3.0;
    private static final double MAX_DETOUR_MINUTES = 15.0;

    private static final List<String> DEFAULT_KAKAO_KEYWORDS = List.of("카페", "맛집", "브런치");
    private static final List<String> DEFAULT_TOUR_TYPES = List.of("12", "14", "28");

    private final KakaoLocalService kakaoLocalService;
    private final TourApiClient tourApiClient;
    private final OverpassClient overpassClient;
    private final KakaoPlaceNormalizer kakaoPlaceNormalizer;
    private final TourPlaceNormalizer tourPlaceNormalizer;
    private final OverpassPlaceNormalizer overpassPlaceNormalizer;
    private final PlaceRuleFilter placeRuleFilter;
    private final RecommendationCacheService cacheService;

    @Value("${external.overpass.enabled:false}")
    private boolean overpassEnabled;

    @Value("${recommendation.cap.kakao:10}")
    private int kakaoCap;

    @Value("${recommendation.cap.tour:20}")
    private int tourCap;

    @Value("${recommendation.cap.overpass:0}")
    private int overpassCap;

    public List<CandidatePlace> collectCandidates(
        GeoPoint origin,
        GeoPoint destination,
        DrivePreference preference
    ) {
        return collectCandidates(origin, destination, preference, CandidateSearchOption.defaultOption());
    }

    public List<CandidatePlace> collectCandidates(
        GeoPoint origin,
        GeoPoint destination,
        DrivePreference preference,
        CandidateSearchOption searchOption
    ) {
        return collectCandidates(origin, destination, preference, searchOption, null);
    }

    public List<CandidatePlace> collectCandidates(
        GeoPoint origin,
        GeoPoint destination,
        DrivePreference preference,
        CandidateSearchOption searchOption,
        RouteCorridor corridor
    ) {
        String cacheKey = buildCandidateCacheKey(origin, destination, preference, searchOption, corridor);
        List<CandidatePlace> cached = cacheService.getCandidatePlaces(cacheKey);
        if (cached != null) {
            log.info("Candidate cache hit - key={}", cacheKey);
            return cached;
        }

        CandidateSearchOption option = searchOption == null
            ? CandidateSearchOption.defaultOption()
            : searchOption;
        List<GeoPoint> searchPoints = buildSearchPoints(origin, destination);
        int searchRadius = corridor == null
            ? option.searchRadiusMeters()
            : corridor.searchRadiusMeters(option.searchRadiusMeters());

        SourceCounter counter = new SourceCounter(
            resolveCap(kakaoCap, DEFAULT_KAKAO_CAP),
            resolveCap(tourCap, DEFAULT_TOUR_CAP),
            resolveCap(overpassCap, DEFAULT_OVERPASS_CAP)
        );
        log.info("Candidate collect config - overpassEnabled={}, kakaoCap={}, tourCap={}, overpassCap={}, searchPoints={}",
            overpassEnabled,
            counter.kakaoCap(),
            counter.tourCap(),
            counter.overpassCap(),
            searchPoints.size());

        Map<String, CandidatePlace> results = new LinkedHashMap<>();
        collectTourPlaces(searchPoints, searchRadius, preference, option, corridor, counter, results);
        collectKakaoPlaces(searchPoints, searchRadius, preference, option, corridor, counter, results);
        collectOverpassPlaces(searchPoints, searchRadius, preference, option, corridor, counter, results);

        log.info("Candidate caps applied - kakao={}, tour={}, overpass={}",
            counter.kakaoCount(), counter.tourCount(), counter.overpassCount());

        List<CandidatePlace> candidates = new ArrayList<>(results.values());
        log.info("후보 장소 수집 완료 - count={}", candidates.size());
        cacheService.putCandidatePlaces(cacheKey, candidates);
        return candidates;
    }

    private void collectTourPlaces(
        List<GeoPoint> searchPoints,
        int searchRadius,
        DrivePreference preference,
        CandidateSearchOption option,
        RouteCorridor corridor,
        SourceCounter counter,
        Map<String, CandidatePlace> results
    ) {
        List<DriveStopType> stopTypes = preference == null ? List.of() : preference.stopTypes();
        for (GeoPoint point : searchPoints) {
            for (String contentType : DEFAULT_TOUR_TYPES) {
                if (counter.tourReached()) {
                    return;
                }
                String cacheKey = buildSearchCacheKey("tour", point, searchRadius, contentType);
                List<TourItem> cached = cacheService.getTourItems(cacheKey);
                if (cached != null) {
                    for (TourItem item : cached) {
                        if (!addTourCandidate(item, stopTypes, option, corridor, counter, results)) {
                            return;
                        }
                    }
                    continue;
                }
                List<TourItem> items = tourApiClient.fetchLocationBased(
                    point.y(),
                    point.x(),
                    searchRadius,
                    TOUR_PAGE,
                    TOUR_SIZE,
                    contentType
                );

                if (items != null) {
                    List<TourItem> docs = items.stream().filter(Objects::nonNull).toList();
                    cacheService.putTourItems(cacheKey, docs);
                    for (TourItem item : docs) {
                        if (!addTourCandidate(item, stopTypes, option, corridor, counter, results)) {
                            return;
                        }
                    }
                }
            }
        }
    }

    private void collectKakaoPlaces(
        List<GeoPoint> searchPoints,
        int searchRadius,
        DrivePreference preference,
        CandidateSearchOption option,
        RouteCorridor corridor,
        SourceCounter counter,
        Map<String, CandidatePlace> results
    ) {
        List<DriveStopType> stopTypes = preference == null ? List.of() : preference.stopTypes();
        Set<String> keywords = buildKakaoKeywords(preference);
        for (GeoPoint point : searchPoints) {
            for (String keyword : keywords) {
                if (counter.kakaoReached()) {
                    return;
                }
                String cacheKey = buildSearchCacheKey("kakao", point, searchRadius, keyword);
                List<KakaoPlaceDocument> cached = cacheService.getKakaoPlaces(cacheKey);
                if (cached != null) {
                    for (KakaoPlaceDocument document : cached) {
                        if (!addKakaoCandidate(document, stopTypes, option, corridor, counter, results)) {
                            return;
                        }
                    }
                    continue;
                }
                KakaoPlaceSearchResponse response = kakaoLocalService.searchKeywordByLocation(
                    keyword,
                    point.x(),
                    point.y(),
                    searchRadius,
                    SEARCH_PAGE,
                    SEARCH_SIZE
                );

                if (response == null || response.documents() == null) {
                    continue;
                }

                List<KakaoPlaceDocument> docs = response.documents().stream()
                    .filter(Objects::nonNull)
                    .toList();
                cacheService.putKakaoPlaces(cacheKey, docs);
                for (KakaoPlaceDocument document : docs) {
                    if (!addKakaoCandidate(document, stopTypes, option, corridor, counter, results)) {
                        return;
                    }
                }
            }
        }
    }

    private void collectOverpassPlaces(
        List<GeoPoint> searchPoints,
        int searchRadius,
        DrivePreference preference,
        CandidateSearchOption option,
        RouteCorridor corridor,
        SourceCounter counter,
        Map<String, CandidatePlace> results
    ) {
        if (!overpassEnabled || counter.overpassCap() == 0) {
            log.info("Overpass collection disabled");
            return;
        }
        List<DriveStopType> stopTypes = preference == null ? List.of() : preference.stopTypes();
        int remaining = Math.min(OVERPASS_LIMIT, counter.overpassCap());
        for (GeoPoint point : searchPoints) {
            if (remaining <= 0) {
                return;
            }
            String cacheKey = buildSearchCacheKey("overpass", point, searchRadius, "osm");
            List<OverpassElement> elements = cacheService.getOverpassElements(cacheKey);
            if (elements == null) {
                OverpassResponse response = overpassClient.executeQuery(buildOverpassQuery(point, searchRadius));
                if (response == null || response.elements() == null) {
                    continue;
                }
                elements = response.elements().stream().filter(Objects::nonNull).toList();
                cacheService.putOverpassElements(cacheKey, elements);
            }
            for (OverpassElement element : elements) {
                if (remaining <= 0) {
                    return;
                }
                CandidatePlace candidate = overpassPlaceNormalizer.normalize(element);
                if (candidate == null || !withinCorridor(candidate, corridor)) {
                    continue;
                }

                FilterDecision decision = placeRuleFilter.filter(candidate);
                if (!decision.passed()) {
                    continue;
                }

                if (option.applyStopTypeFilter() && !matchesStopTypes(candidate, stopTypes)) {
                    continue;
                }

                if (putIfAllowed(candidate, counter, results, CandidateSource.OVERPASS)) {
                    remaining--;
                }
            }
        }
    }

    private List<GeoPoint> buildSearchPoints(GeoPoint origin, GeoPoint destination) {
        List<GeoPoint> points = new ArrayList<>();
        points.add(origin);
        points.add(destination);
        points.addAll(generateMidpoints(origin, destination, MIDPOINT_COUNT));
        return points;
    }

    private List<GeoPoint> generateMidpoints(GeoPoint origin, GeoPoint destination, int count) {
        List<GeoPoint> points = new ArrayList<>();
        if (count <= 0) {
            return points;
        }

        for (int index = 1; index <= count; index++) {
            double ratio = index / (double) (count + 1);
            double x = origin.x() + (destination.x() - origin.x()) * ratio;
            double y = origin.y() + (destination.y() - origin.y()) * ratio;
            points.add(new GeoPoint(x, y));
        }

        return points;
    }

    private Set<String> buildKakaoKeywords(DrivePreference preference) {
        Set<String> keywords = new LinkedHashSet<>(DEFAULT_KAKAO_KEYWORDS);
        if (preference == null) {
            return keywords;
        }

        preference.stopTypes().stream()
            .filter(type -> !type.requiredGroupCodes().isEmpty())
            .flatMap(type -> type.keywords().stream())
            .filter(Objects::nonNull)
            .map(String::trim)
            .filter(value -> !value.isBlank())
            .forEach(keywords::add);

        return keywords;
    }

    private boolean withinCorridor(CandidatePlace candidate, RouteCorridor corridor) {
        if (corridor == null) {
            return true;
        }
        GeoPoint point = new GeoPoint(candidate.x(), candidate.y());
        if (!corridor.contains(point)) {
            return false;
        }
        return passesDetourPolicy(candidate, corridor);
    }

    private boolean passesDetourPolicy(CandidatePlace candidate, RouteCorridor corridor) {
        RouteMetrics metrics = corridor.metrics();
        if (metrics == null) {
            return false;
        }
        GeoPoint origin = corridor.origin();
        GeoPoint destination = corridor.destination();
        GeoPoint point = new GeoPoint(candidate.x(), candidate.y());
        double detourKm = io.routepickapi.service.recommendation.GeoUtils.distanceKm(origin, point)
            + io.routepickapi.service.recommendation.GeoUtils.distanceKm(point, destination)
            - metrics.baseDistanceKm();
        double deviationKm = Math.max(0.0, detourKm);
        double delayMinutes = io.routepickapi.service.recommendation.GeoUtils.estimateMinutes(deviationKm);
        return deviationKm <= MAX_ROUTE_DEVIATION_KM && delayMinutes <= MAX_DETOUR_MINUTES;
    }

    private boolean addTourCandidate(
        TourItem item,
        List<DriveStopType> stopTypes,
        CandidateSearchOption option,
        RouteCorridor corridor,
        SourceCounter counter,
        Map<String, CandidatePlace> results
    ) {
        if (counter.tourReached()) {
            return false;
        }
        CandidatePlace candidate = tourPlaceNormalizer.normalize(item);
        if (candidate == null || !withinCorridor(candidate, corridor)) {
            return true;
        }

        FilterDecision decision = placeRuleFilter.filter(candidate);
        if (!decision.passed()) {
            return true;
        }

        if (option.applyStopTypeFilter() && !matchesStopTypes(candidate, stopTypes)) {
            return true;
        }

        putIfAllowed(candidate, counter, results, CandidateSource.TOURAPI);
        return !counter.tourReached();
    }

    private boolean addKakaoCandidate(
        KakaoPlaceDocument document,
        List<DriveStopType> stopTypes,
        CandidateSearchOption option,
        RouteCorridor corridor,
        SourceCounter counter,
        Map<String, CandidatePlace> results
    ) {
        if (counter.kakaoReached()) {
            return false;
        }
        CandidatePlace candidate = kakaoPlaceNormalizer.normalize(document);
        if (candidate == null || !withinCorridor(candidate, corridor)) {
            return true;
        }

        FilterDecision decision = placeRuleFilter.filter(candidate);
        if (!decision.passed()) {
            return true;
        }

        if (option.applyStopTypeFilter() && !matchesStopTypes(candidate, stopTypes)) {
            return true;
        }

        putIfAllowed(candidate, counter, results, CandidateSource.KAKAO);
        return !counter.kakaoReached();
    }

    private boolean putIfAllowed(
        CandidatePlace candidate,
        SourceCounter counter,
        Map<String, CandidatePlace> results,
        CandidateSource source
    ) {
        if (source == CandidateSource.TOURAPI && counter.tourReached()) {
            return false;
        }
        if (source == CandidateSource.KAKAO && counter.kakaoReached()) {
            return false;
        }
        if (source == CandidateSource.OVERPASS && counter.overpassReached()) {
            return false;
        }
        boolean added = results.putIfAbsent(candidate.key(), candidate) == null;
        if (added) {
            counter.increment(source);
        }
        return added;
    }

    private int resolveCap(int value, int fallback) {
        return value > 0 ? value : fallback;
    }

    private String buildCandidateCacheKey(
        GeoPoint origin,
        GeoPoint destination,
        DrivePreference preference,
        CandidateSearchOption option,
        RouteCorridor corridor
    ) {
        int radius = corridor == null
            ? option.searchRadiusMeters()
            : corridor.searchRadiusMeters(option.searchRadiusMeters());
        return "candidate-bundle:"
            + formatPoint(origin)
            + ":"
            + formatPoint(destination)
            + ":r=" + radius
            + ":pref=" + preferenceKey(preference)
            + ":stopFilter=" + option.applyStopTypeFilter();
    }

    private String buildSearchCacheKey(String source, GeoPoint point, int radius, String keyword) {
        return "place-search:" + source + ":" + formatPoint(point) + ":r=" + radius + ":q=" + keyword;
    }

    private String formatPoint(GeoPoint point) {
        return String.format("%.5f,%.5f", point.y(), point.x());
    }

    private String preferenceKey(DrivePreference preference) {
        if (preference == null) {
            return "none";
        }
        return preference.moods() + ":" + preference.stopTypes() + ":" + preference.routeStyles();
    }

    private String buildOverpassQuery(GeoPoint point, int radiusMeters) {
        return String.format(
            "(node[\"tourism\"~\"viewpoint|attraction\"](around:%d,%f,%f);"
                + "node[\"natural\"~\"peak|beach|coastline|bay|wood|cliff|ridge|waterfall\"](around:%d,%f,%f);"
                + "node[\"man_made\"=\"lookout\"](around:%d,%f,%f);"
                + "node[\"scenic\"=\"yes\"](around:%d,%f,%f);"
                + "way[\"tourism\"~\"viewpoint|attraction\"](around:%d,%f,%f);"
                + "way[\"natural\"~\"peak|beach|coastline|bay|wood|cliff|ridge|waterfall\"](around:%d,%f,%f);" + ");out center tags;",
            radiusMeters,
            point.y(),
            point.x(),
            radiusMeters,
            point.y(),
            point.x(),
            radiusMeters,
            point.y(),
            point.x(),
            radiusMeters,
            point.y(),
            point.x(),
            radiusMeters,
            point.y(),
            point.x(),
            radiusMeters,
            point.y(),
            point.x()
        );
    }

    private boolean matchesStopTypes(CandidatePlace candidate, List<DriveStopType> stopTypes) {
        if (stopTypes == null || stopTypes.isEmpty()) {
            return true;
        }

        return stopTypes.stream().anyMatch(stopType -> matchesStopType(candidate, stopType));
    }

    private boolean matchesStopType(CandidatePlace candidate, DriveStopType stopType) {
        if (stopType == null) {
            return false;
        }

        String value = String.join(" ",
            safeLower(candidate.name()),
            safeLower(candidate.categoryName()),
            safeLower(candidate.categoryGroupName())
        );

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

    private String safeLower(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static final class SourceCounter {
        private final int kakaoCap;
        private final int tourCap;
        private final int overpassCap;
        private int kakaoCount;
        private int tourCount;
        private int overpassCount;

        private SourceCounter(int kakaoCap, int tourCap, int overpassCap) {
            this.kakaoCap = kakaoCap;
            this.tourCap = tourCap;
            this.overpassCap = overpassCap;
        }

        int kakaoCap() {
            return kakaoCap;
        }

        int tourCap() {
            return tourCap;
        }

        int overpassCap() {
            return overpassCap;
        }

        int kakaoCount() {
            return kakaoCount;
        }

        int tourCount() {
            return tourCount;
        }

        int overpassCount() {
            return overpassCount;
        }

        boolean kakaoReached() {
            return kakaoCap > 0 && kakaoCount >= kakaoCap;
        }

        boolean tourReached() {
            return tourCap > 0 && tourCount >= tourCap;
        }

        boolean overpassReached() {
            return overpassCap > 0 && overpassCount >= overpassCap;
        }

        void increment(CandidateSource source) {
            if (source == CandidateSource.KAKAO) {
                kakaoCount++;
            } else if (source == CandidateSource.TOURAPI) {
                tourCount++;
            } else if (source == CandidateSource.OVERPASS) {
                overpassCount++;
            }
        }
    }
}
