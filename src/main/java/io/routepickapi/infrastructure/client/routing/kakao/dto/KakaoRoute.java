package io.routepickapi.infrastructure.client.routing.kakao.dto;

import java.util.List;

public record KakaoRoute(
    KakaoSummary summary,
    List<KakaoSection> sections
) {
}
