package io.routepickapi.controller;

import io.routepickapi.dto.course.CourseRecommendationRequest;
import io.routepickapi.dto.course.CourseRecommendationResponse;
import io.routepickapi.dto.course.CourseRecommendationSaveRequest;
import io.routepickapi.dto.course.CourseRecommendationSaveResponse;
import io.routepickapi.security.AuthUser;
import io.routepickapi.service.CourseRecommendationSaveService;
import io.routepickapi.service.CourseRecommendationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/courses")
public class CourseController {

    private final CourseRecommendationService courseRecommendationService;
    private final CourseRecommendationSaveService courseRecommendationSaveService;

    @Operation(summary = "드라이브 코스 추천", description = "출발지/도착지/추천 조건 기반으로 코스를 추천합니다.")
    @PostMapping("/recommend")
    public CourseRecommendationResponse recommend(
        @Valid @RequestBody CourseRecommendationRequest request
    ) {
        log.info(
            "POST /courses/recommend - moods={}, stopTypes={}, routeStyles={}, auto={}",
            request.moods(),
            request.stopTypes(),
            request.routeStyles(),
            request.autoRecommend()
        );
        return courseRecommendationService.recommend(request);
    }
    @Operation(summary = "추천 코스 저장", description = "추천 결과를 내 저장 목록에 추가합니다.",
        security = {@SecurityRequirement(name = "bearerAuth")})
    @PostMapping("/saved")
    @PreAuthorize("isAuthenticated()")
    public CourseRecommendationSaveResponse saveRecommendation(
        @Valid @RequestBody CourseRecommendationSaveRequest request,
        @AuthenticationPrincipal AuthUser currentUser
    ) {
        log.info("POST /courses/saved - userId={}", currentUser.id());
        return courseRecommendationSaveService.saveRecommendation(request, currentUser.id());
    }

    @Operation(summary = "추천 코스 저장 목록", description = "내가 저장한 추천 코스를 조회합니다.",
        security = {@SecurityRequirement(name = "bearerAuth")})
    @GetMapping("/saved")
    @PreAuthorize("isAuthenticated()")
    public Page<CourseRecommendationSaveResponse> listSaved(
        @AuthenticationPrincipal AuthUser currentUser,
        @ParameterObject
        @PageableDefault(size = 5, sort = "createdAt", direction = Sort.Direction.DESC)
        Pageable pageable
    ) {
        log.info("GET /courses/saved - userId={}", currentUser.id());
        return courseRecommendationSaveService.listSaved(currentUser.id(), pageable);
    }

    @Operation(summary = "추천 코스 삭제", description = "내가 저장한 추천 코스를 삭제합니다.",
        security = {@SecurityRequirement(name = "bearerAuth")})
    @DeleteMapping("/saved/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> deleteSaved(
        @AuthenticationPrincipal AuthUser currentUser,
        @PathVariable(name = "id") @Min(1) Long id
    ) {
        log.info("DELETE /courses/saved/{} - userId={}", id, currentUser.id());
        courseRecommendationSaveService.deleteSaved(id, currentUser.id());
        return ResponseEntity.noContent().build();
    }

    
}
