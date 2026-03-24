package io.routepickapi.service.recommendation.pipeline;

import io.routepickapi.common.error.CustomException;
import io.routepickapi.common.error.ErrorType;
import io.routepickapi.dto.place.KakaoPlaceSearchResponse;
import io.routepickapi.dto.place.KakaoPlaceSearchResponse.KakaoPlaceDocument;
import io.routepickapi.infrastructure.client.kakao.KakaoLocalClient;
import io.routepickapi.infrastructure.client.tour.TourApiClient;
import io.routepickapi.infrastructure.client.tour.dto.TourItem;
import io.routepickapi.service.recommendation.RecommendationCacheService;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PoiCollectorService {

    private static final int MIDPOINT_COUNT = 3;
    private static final int SEARCH_PAGE = 1;
    private static final int SEARCH_SIZE = 15;
    private static final int TOUR_PAGE = 1;
    private static final int TOUR_SIZE = 100;
    private static final int DEFAULT_RADIUS = 10000;
    private static final int DEFAULT_KAKAO_CAP = 10;
    private static final int DEFAULT_TOUR_CAP = 20;

    private static final List<String> DEFAULT_KAKAO_KEYWORDS = List.of(
        "전망대",
        "드라이브",
        "해변",
        "해안",
        "공원",
        "카페",
        "맛집",
        "호수",
        "산",
        "자연"
    );

    private static final List<String> DEFAULT_TOUR_CONTENT_TYPES = List.of("12", "28", "39");

    private final KakaoLocalClient kakaoLocalClient;
    private final TourApiClient tourApiClient;
    private final RecommendationCacheService cacheService;

    @org.springframework.beans.factory.annotation.Value("${recommendation.cap.kakao:10}")
    private int kakaoCap;

    @org.springframework.beans.factory.annotation.Value("${recommendation.cap.tour:20}")
    private int tourCap;

    public RawPoiBundle collect(PoiCollectionRequest request) {
        if (request == null) {
            throw new CustomException(ErrorType.COMMON_INVALID_INPUT, "수집 요청이 비어있습니다.");
        }

        int radius = request.radiusMeters() > 0 ? request.radiusMeters() : DEFAULT_RADIUS;
        List<String> kakaoKeywords = request.kakaoKeywords() == null || request.kakaoKeywords().isEmpty()
            ? DEFAULT_KAKAO_KEYWORDS
            : request.kakaoKeywords();
        List<String> tourContentTypes;
        if (request.tourContentTypeIds() == null) {
            tourContentTypes = DEFAULT_TOUR_CONTENT_TYPES;
        } else if (request.tourContentTypeIds().isEmpty()) {
            tourContentTypes = List.of();
        } else {
            tourContentTypes = request.tourContentTypeIds();
        }
        List<SearchPoint> searchPoints = buildSearchPoints(request);
        log.info("POI collect config - kakaoCap={}, tourCap={}, searchPoints={}",
            kakaoCap,
            tourCap,
            searchPoints.size());

        String cacheKey = buildBundleCacheKey(request, radius, kakaoKeywords, tourContentTypes, searchPoints);
        RawPoiBundle cached = cacheService.getPoiBundle(cacheKey);
        if (cached != null) {
            log.info("POI bundle cache hit - key={}", cacheKey);
            return cached;
        }

        List<KakaoPlaceDocument> kakaoPlaces = collectKakaoPlaces(
            searchPoints,
            radius,
            kakaoKeywords
        );
        List<TourItem> tourItems = collectTourItems(
            searchPoints,
            radius,
            tourContentTypes
        );
        RawPoiBundle bundle = new RawPoiBundle(kakaoPlaces, tourItems);
        cacheService.putPoiBundle(cacheKey, bundle);
        return bundle;
    }

    private List<KakaoPlaceDocument> collectKakaoPlaces(
        List<SearchPoint> searchPoints,
        int radius,
        List<String> keywords
    ) {
        List<KakaoPlaceDocument> results = new ArrayList<>();
        int cap = Math.max(1, kakaoCap > 0 ? kakaoCap : DEFAULT_KAKAO_CAP);
        for (SearchPoint point : searchPoints) {
            for (String keyword : keywords) {
                if (keyword == null || keyword.isBlank()) {
                    continue;
                }

                if (results.size() >= cap) {
                    break;
                }

                String cacheKey = buildSearchCacheKey("kakao", point, radius, keyword);
                List<KakaoPlaceDocument> cached = cacheService.getKakaoPlaces(cacheKey);
                if (cached != null) {
                    cached.stream().filter(Objects::nonNull).forEach(results::add);
                    continue;
                }

                KakaoPlaceSearchResponse response = kakaoLocalClient.searchKeywordByLocation(
                    keyword,
                    point.lng(),
                    point.lat(),
                    radius,
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
                docs.forEach(results::add);
            }
            if (results.size() >= cap) {
                break;
            }
        }

        List<KakaoPlaceDocument> capped = applyCap(results, cap, "kakao");
        log.info("Kakao POI 수집 완료 - count={}", capped.size());
        return capped;
    }

    private List<TourItem> collectTourItems(
        List<SearchPoint> searchPoints,
        int radius,
        List<String> contentTypes
    ) {
        if (contentTypes == null || contentTypes.isEmpty()) {
            log.info("TourAPI POI 수집 skipped - reason=disabled");
            return List.of();
        }
        List<TourItem> results = new ArrayList<>();
        int cap = Math.max(1, tourCap > 0 ? tourCap : DEFAULT_TOUR_CAP);
        for (SearchPoint point : searchPoints) {
            for (String contentType : contentTypes) {
                if (results.size() >= cap) {
                    break;
                }
                String cacheKey = buildSearchCacheKey("tour", point, radius, contentType);
                List<TourItem> cached = cacheService.getTourItems(cacheKey);
                if (cached != null) {
                    cached.stream().filter(Objects::nonNull).forEach(results::add);
                    continue;
                }
                List<TourItem> items = tourApiClient.fetchLocationBased(
                    point.lat(),
                    point.lng(),
                    radius,
                    TOUR_PAGE,
                    TOUR_SIZE,
                    contentType
                );

                if (items != null) {
                    List<TourItem> docs = items.stream().filter(Objects::nonNull).toList();
                    cacheService.putTourItems(cacheKey, docs);
                    docs.forEach(results::add);
                }
            }
            if (results.size() >= cap) {
                break;
            }
        }

        List<TourItem> capped = applyCap(results, cap, "tour");
        log.info("TourAPI POI 수집 완료 - count={}", capped.size());
        return capped;
    }

    private List<SearchPoint> buildSearchPoints(PoiCollectionRequest request) {
        List<SearchPoint> points = new ArrayList<>();
        points.add(new SearchPoint(request.originLat(), request.originLng()));
        points.add(new SearchPoint(request.destinationLat(), request.destinationLng()));
        points.addAll(generateMidpoints(request.originLat(), request.originLng(),
            request.destinationLat(), request.destinationLng(), MIDPOINT_COUNT));
        return points;
    }

    private List<SearchPoint> generateMidpoints(
        double originLat,
        double originLng,
        double destinationLat,
        double destinationLng,
        int count
    ) {
        List<SearchPoint> points = new ArrayList<>();
        if (count <= 0) {
            return points;
        }

        for (int index = 1; index <= count; index++) {
            double ratio = index / (double) (count + 1);
            double lat = originLat + (destinationLat - originLat) * ratio;
            double lng = originLng + (destinationLng - originLng) * ratio;
            points.add(new SearchPoint(lat, lng));
        }

        return points;
    }

    private record SearchPoint(double lat, double lng) {
    }

    private <T> List<T> applyCap(List<T> items, int cap, String label) {
        if (items == null) {
            return List.of();
        }
        int before = items.size();
        List<T> capped = cap > 0 && items.size() > cap
            ? new ArrayList<>(items.subList(0, cap))
            : new ArrayList<>(items);
        log.info("{} cap applied - before={}, after={}, cap={}", label, before, capped.size(), cap);
        return capped;
    }

    private String buildBundleCacheKey(
        PoiCollectionRequest request,
        int radius,
        List<String> kakaoKeywords,
        List<String> tourContentTypes,
        List<SearchPoint> searchPoints
    ) {
        return "poi-bundle:"
            + formatPoint(request.originLat(), request.originLng())
            + ":"
            + formatPoint(request.destinationLat(), request.destinationLng())
            + ":r=" + radius
            + ":k=" + String.join("|", kakaoKeywords)
            + ":t=" + String.join("|", tourContentTypes)
            + ":p=" + searchPoints.size();
    }

    private String buildSearchCacheKey(String source, SearchPoint point, int radius, String keyword) {
        return "poi-search:" + source + ":" + formatPoint(point.lat(), point.lng())
            + ":r=" + radius + ":q=" + keyword;
    }

    private String formatPoint(double lat, double lng) {
        return String.format("%.5f,%.5f", lat, lng);
    }

}
