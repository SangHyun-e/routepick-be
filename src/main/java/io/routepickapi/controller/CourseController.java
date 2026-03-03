package io.routepickapi.controller;

import io.routepickapi.dto.course.CourseRecommendationRequest;
import io.routepickapi.dto.course.CourseRecommendationResponse;
import io.routepickapi.service.CourseRecommendationService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/courses")
public class CourseController {

    private final CourseRecommendationService courseRecommendationService;

    @Operation(summary = "드라이브 코스 추천", description = "출발지/도착지/테마 기반으로 코스를 추천합니다.")
    @PostMapping("/recommend")
    public CourseRecommendationResponse recommend(
        @Valid @RequestBody CourseRecommendationRequest request
    ) {
        log.info("POST /courses/recommend - theme={}", request.theme());
        return courseRecommendationService.recommend(request);
    }
}
