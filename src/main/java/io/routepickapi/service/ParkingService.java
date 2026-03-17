package io.routepickapi.service;

import io.routepickapi.dto.parking.NearbyParkingItemResponse;
import io.routepickapi.dto.place.KakaoPlaceSearchResponse;
import io.routepickapi.dto.place.KakaoPlaceSearchResponse.KakaoPlaceDocument;
import io.routepickapi.infrastructure.client.kakao.KakaoLocalClient;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ParkingService {

    private static final String PARKING_KEYWORD = "주차장";
    private static final int RADIUS_METERS = 3000;
    private static final int RESULT_LIMIT = 5;

    private final KakaoLocalClient kakaoLocalClient;

    public List<NearbyParkingItemResponse> findNearby(double latitude, double longitude) {
        log.info("Nearby parking lookup - lat={}, lng={}", latitude, longitude);

        try {
            KakaoPlaceSearchResponse response = kakaoLocalClient.searchKeywordByLocation(
                PARKING_KEYWORD,
                longitude,
                latitude,
                RADIUS_METERS,
                1,
                RESULT_LIMIT
            );

            List<KakaoPlaceDocument> documents = response == null || response.documents() == null
                ? List.of()
                : response.documents();
            log.info("Kakao parking response - count={}", documents.size());

            List<NearbyParkingItemResponse> items = documents.stream()
                .map(this::toResponse)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(NearbyParkingItemResponse::distanceMeters))
                .limit(RESULT_LIMIT)
                .toList();

            log.info("Nearby parking result - count={}", items.size());
            return items;
        } catch (Exception ex) {
            log.warn("Nearby parking lookup failed", ex);
            return List.of();
        }
    }

    private NearbyParkingItemResponse toResponse(KakaoPlaceDocument document) {
        if (document == null || document.placeName() == null || document.placeName().isBlank()) {
            return null;
        }

        String address = resolveAddress(document);
        int distanceMeters = parseDistance(document.distance());
        return new NearbyParkingItemResponse(
            document.placeName(),
            address,
            distanceMeters
        );
    }

    private String resolveAddress(KakaoPlaceDocument document) {
        if (document.roadAddressName() != null && !document.roadAddressName().isBlank()) {
            return document.roadAddressName();
        }
        if (document.addressName() != null && !document.addressName().isBlank()) {
            return document.addressName();
        }
        return "주소 정보 없음";
    }

    private int parseDistance(String raw) {
        if (raw == null || raw.isBlank()) {
            return 0;
        }
        try {
            return Math.max(0, Integer.parseInt(raw));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }
}
