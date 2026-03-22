package io.routepickapi.infrastructure.client.kakao.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

public record KakaoCoordDocument(
    KakaoAddress address,
    @JsonAlias("road_address") KakaoRoadAddress roadAddress
) {
}
