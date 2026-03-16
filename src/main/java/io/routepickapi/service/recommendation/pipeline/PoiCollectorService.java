package io.routepickapi.service.recommendation.pipeline;

import io.routepickapi.common.error.CustomException;
import io.routepickapi.common.error.ErrorType;
import io.routepickapi.dto.place.KakaoPlaceSearchResponse;
import io.routepickapi.dto.place.KakaoPlaceSearchResponse.KakaoPlaceDocument;
import io.routepickapi.infrastructure.client.kakao.KakaoLocalClient;
import io.routepickapi.infrastructure.client.overpass.OverpassClient;
import io.routepickapi.infrastructure.client.overpass.OverpassClient.OverpassResponse;
import io.routepickapi.infrastructure.client.tour.TourApiClient;
import io.routepickapi.infrastructure.client.tour.TourApiClient.TourItem;
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

    private static final int SEARCH_PAGE = 1;
    private static final int SEARCH_SIZE = 15;
    private static final int TOUR_PAGE = 1;
    private static final int TOUR_SIZE = 100;
    private static final int DEFAULT_RADIUS = 10000;

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
    private final OverpassClient overpassClient;

    public RawPoiBundle collect(PoiCollectionRequest request) {
        if (request == null) {
            throw new CustomException(ErrorType.COMMON_INVALID_INPUT, "수집 요청이 비어있습니다.");
        }

        int radius = request.radiusMeters() > 0 ? request.radiusMeters() : DEFAULT_RADIUS;
        List<String> kakaoKeywords = request.kakaoKeywords() == null || request.kakaoKeywords().isEmpty()
            ? DEFAULT_KAKAO_KEYWORDS
            : request.kakaoKeywords();
        List<String> tourContentTypes = request.tourContentTypeIds() == null || request.tourContentTypeIds().isEmpty()
            ? DEFAULT_TOUR_CONTENT_TYPES
            : request.tourContentTypeIds();

        List<KakaoPlaceDocument> kakaoPlaces = collectKakaoPlaces(
            request.centerLat(),
            request.centerLng(),
            radius,
            kakaoKeywords
        );
        List<TourItem> tourItems = collectTourItems(
            request.centerLat(),
            request.centerLng(),
            radius,
            tourContentTypes
        );
        List<OverpassClient.OverpassElement> overpassElements = collectOverpassElements(
            request.centerLat(),
            request.centerLng(),
            radius
        );

        return new RawPoiBundle(kakaoPlaces, tourItems, overpassElements);
    }

    private List<KakaoPlaceDocument> collectKakaoPlaces(
        double centerLat,
        double centerLng,
        int radius,
        List<String> keywords
    ) {
        List<KakaoPlaceDocument> results = new ArrayList<>();
        for (String keyword : keywords) {
            if (keyword == null || keyword.isBlank()) {
                continue;
            }

            KakaoPlaceSearchResponse response = kakaoLocalClient.searchKeywordByLocation(
                keyword,
                centerLng,
                centerLat,
                radius,
                SEARCH_PAGE,
                SEARCH_SIZE
            );

            if (response == null || response.documents() == null) {
                continue;
            }

            response.documents().stream()
                .filter(Objects::nonNull)
                .forEach(results::add);
        }

        log.info("Kakao POI 수집 완료 - count={}", results.size());
        return results;
    }

    private List<TourItem> collectTourItems(
        double centerLat,
        double centerLng,
        int radius,
        List<String> contentTypes
    ) {
        List<TourItem> results = new ArrayList<>();

        for (String contentType : contentTypes) {
            List<TourItem> items = tourApiClient.fetchLocationBased(
                centerLat,
                centerLng,
                radius,
                TOUR_PAGE,
                TOUR_SIZE,
                contentType
            );

            if (items != null) {
                items.stream().filter(Objects::nonNull).forEach(results::add);
            }
        }

        log.info("TourAPI POI 수집 완료 - count={}", results.size());
        return results;
    }

    private List<OverpassClient.OverpassElement> collectOverpassElements(
        double centerLat,
        double centerLng,
        int radius
    ) {
        String query = buildOverpassQuery(centerLat, centerLng, radius);
        OverpassResponse response = overpassClient.executeQuery(query);
        if (response == null || response.elements() == null) {
            return List.of();
        }

        log.info("Overpass POI 수집 완료 - count={}", response.elements().size());
        return response.elements();
    }

    private String buildOverpassQuery(double centerLat, double centerLng, int radius) {
        return String.format(
            "(node[\"tourism\"~\"viewpoint|attraction\"](around:%d,%f,%f);"
                + "node[\"natural\"~\"peak|wood|beach|bay|coastline\"](around:%d,%f,%f);"
                + "node[\"leisure\"=\"park\"](around:%d,%f,%f);"
                + "way[\"natural\"~\"peak|wood|beach|bay|coastline\"](around:%d,%f,%f);"
                + "way[\"leisure\"=\"park\"](around:%d,%f,%f);"
                + "rel[\"natural\"~\"peak|wood|beach|bay|coastline\"](around:%d,%f,%f);" + ");out center tags;",
            radius,
            centerLat,
            centerLng,
            radius,
            centerLat,
            centerLng,
            radius,
            centerLat,
            centerLng,
            radius,
            centerLat,
            centerLng,
            radius,
            centerLat,
            centerLng,
            radius,
            centerLat,
            centerLng
        );
    }
}
