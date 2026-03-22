package io.routepickapi.infrastructure.client.kakao.dto;

import java.util.List;

public record KakaoCoordToAddressResponse(List<KakaoCoordDocument> documents) {
}
