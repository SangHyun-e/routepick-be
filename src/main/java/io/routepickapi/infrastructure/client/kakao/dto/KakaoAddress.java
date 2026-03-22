package io.routepickapi.infrastructure.client.kakao.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

public record KakaoAddress(
    @JsonAlias("address_name") String addressName,
    @JsonAlias("region_1depth_name") String region1DepthName,
    @JsonAlias("region_2depth_name") String region2DepthName,
    @JsonAlias("region_3depth_name") String region3DepthName,
    @JsonAlias("main_address_no") String mainAddressNo,
    @JsonAlias("sub_address_no") String subAddressNo
) {
}
