package io.routepickapi.infrastructure.client.tour.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

public record TourItem(
    String contentid,
    String contenttypeid,
    String title,
    String addr1,
    String addr2,
    String mapx,
    String mapy,
    String areacode,
    String sigungucode,
    String cat1,
    String cat2,
    String cat3,
    String firstimage,
    @JsonAlias("firstimage2") String firstImage2
) {
}
