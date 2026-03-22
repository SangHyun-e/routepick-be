package io.routepickapi.infrastructure.client.kakao.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

public record KakaoRoadAddress(
    @JsonAlias("address_name") String addressName,
    @JsonAlias("region_1depth_name") String region1DepthName,
    @JsonAlias("region_2depth_name") String region2DepthName,
    @JsonAlias("region_3depth_name") String region3DepthName,
    @JsonAlias("road_name") String roadName,
    @JsonAlias("main_building_no") String mainBuildingNo,
    @JsonAlias("sub_building_no") String subBuildingNo,
    @JsonAlias("building_name") String buildingName,
    @JsonAlias("zone_no") String zoneNo
) {
}
