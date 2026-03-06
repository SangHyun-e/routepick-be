package io.routepickapi.controller;

import io.routepickapi.dto.place.KakaoPlaceSearchResponse;
import io.routepickapi.service.KakaoLocalService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/places")
@Validated
public class PlaceController {

    private final KakaoLocalService kakaoLocalService;

    @Operation(summary = "장소 검색", description = "Kakao Local Search API 프록시")
    @GetMapping("/search")
    public KakaoPlaceSearchResponse search(
        @RequestParam String keyword,
        @RequestParam(defaultValue = "1") @Min(1) @Max(45) int page,
        @RequestParam(defaultValue = "10") @Min(1) @Max(15) int size
    ) {
        return kakaoLocalService.searchKeyword(keyword, page, size);
    }
}
