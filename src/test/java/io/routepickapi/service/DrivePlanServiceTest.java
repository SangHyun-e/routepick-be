package io.routepickapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.routepickapi.dto.drive.DrivePlanRequest;
import io.routepickapi.dto.drive.DrivePlanResponse;
import io.routepickapi.dto.place.KakaoPlaceSearchResponse;
import io.routepickapi.dto.place.KakaoPlaceSearchResponse.KakaoPlaceDocument;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DrivePlanServiceTest {

    @Mock
    private KakaoLocalService kakaoLocalService;

    @Mock
    private DrivePlanLlmClient drivePlanLlmClient;

    private DrivePlanService drivePlanService;

    @BeforeEach
    void setUp() {
        drivePlanService = new DrivePlanService(kakaoLocalService, drivePlanLlmClient, new ObjectMapper());
    }

    @Test
    void returnsFallbackPlanWhenLlmUnavailable() {
        DrivePlanRequest request = new DrivePlanRequest("서울", "부산", "SEA", true);

        when(drivePlanLlmClient.requestPlan(anyString())).thenReturn(Optional.empty());

        KakaoPlaceSearchResponse startResponse = new KakaoPlaceSearchResponse(null, List.of(
            document("1", "서울 카페", "카페", "서울시 중구", "서울시 중구", "https://place.map/1", "127.1", "37.1"),
            document("2", "서울 전망대", "관광", "서울시 중구", "서울시 중구", "https://place.map/2", "127.2", "37.2"),
            document("3", "서울 휴게소", "휴게소", "서울시 중구", "서울시 중구", "https://place.map/3", "127.3", "37.3")
        ));

        KakaoPlaceSearchResponse endResponse = new KakaoPlaceSearchResponse(null, List.of(
            document("4", "부산 카페", "카페", "부산시 해운대", "부산시 해운대", "https://place.map/4", "129.1", "35.1"),
            document("5", "부산 식당", "음식점", "부산시 해운대", "부산시 해운대", "https://place.map/5", "129.2", "35.2"),
            document("6", "부산 전망", "관광", "부산시 해운대", "부산시 해운대", "https://place.map/6", "129.3", "35.3")
        ));

        KakaoPlaceSearchResponse emptyResponse = new KakaoPlaceSearchResponse(null, List.of());

        when(kakaoLocalService.searchKeyword("서울", 1, 15)).thenReturn(startResponse);
        when(kakaoLocalService.searchKeyword("서울", 2, 5)).thenReturn(emptyResponse);
        when(kakaoLocalService.searchKeyword("부산", 1, 15)).thenReturn(endResponse);
        when(kakaoLocalService.searchKeyword("부산", 2, 5)).thenReturn(emptyResponse);

        DrivePlanResponse response = drivePlanService.plan(request);

        assertThat(response.stops()).hasSize(5);
        assertThat(response.courseName()).contains("서울");
        assertThat(response.planReason()).isEqualTo("후보 장소 중 상위 결과로 기본 코스를 구성했습니다.");
        assertThat(response.stops().get(0).openNowEstimated()).isTrue();
    }

    private KakaoPlaceDocument document(
        String id,
        String name,
        String categoryName,
        String address,
        String roadAddress,
        String placeUrl,
        String x,
        String y
    ) {
        return new KakaoPlaceDocument(
            id,
            name,
            categoryName,
            null,
            null,
            null,
            address,
            roadAddress,
            placeUrl,
            x,
            y,
            null
        );
    }
}
