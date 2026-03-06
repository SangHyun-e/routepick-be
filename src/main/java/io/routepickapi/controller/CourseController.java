package io.routepickapi.controller;

import io.routepickapi.dto.course.CourseCurationRequest;
import io.routepickapi.dto.course.CourseCurationResponse;
import io.routepickapi.dto.course.CourseRecommendationRequest;
import io.routepickapi.dto.course.CourseRecommendationResponse;
import io.routepickapi.dto.course.CourseRecommendationSaveRequest;
import io.routepickapi.dto.course.CourseRecommendationSaveResponse;
import io.routepickapi.entity.notification.NotificationResourceType;
import io.routepickapi.entity.notification.NotificationType;
import io.routepickapi.security.AuthUser;
import io.routepickapi.service.CourseCurationService;
import io.routepickapi.service.CourseRecommendationSaveService;
import io.routepickapi.service.CourseRecommendationService;
import io.routepickapi.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
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
    private final CourseCurationService courseCurationService;
    private final NotificationService notificationService;

    @Operation(summary = "드라이브 코스 추천", description = "출발지/도착지/테마 기반으로 코스를 추천합니다.")
    @PostMapping("/recommend")
    public CourseRecommendationResponse recommend(
        @Valid @RequestBody CourseRecommendationRequest request
    ) {
        log.info("POST /courses/recommend - theme={}", request.theme());
        return courseRecommendationService.recommend(request);
    }

    @Operation(summary = "크루저 큐레이션", description = "추천 코스 기반으로 크루저 큐레이션을 생성합니다.",
        security = {@SecurityRequirement(name = "bearerAuth")})
    @PostMapping("/curation")
    @PreAuthorize("isAuthenticated()")
    public CourseCurationResponse curate(
        @Valid @RequestBody CourseCurationRequest request,
        @AuthenticationPrincipal AuthUser currentUser,
        HttpServletRequest httpServletRequest
    ) {
        String rateLimitKey = resolveRateLimitKey(currentUser, httpServletRequest);
        log.info("POST /courses/curation - key={}", rateLimitKey);
        CourseCurationResponse response = courseCurationService.curate(request, rateLimitKey);
        if (currentUser != null) {
            notificationService.createNotificationByUserId(
                currentUser.id(),
                NotificationType.COURSE_READY,
                "추천 결과가 준비됐어요",
                "요청하신 드라이브 코스 추천이 완료됐어요.",
                NotificationResourceType.COURSE,
                null,
                null,
                null,
                null
            );
        }
        return response;
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

    private String resolveRateLimitKey(AuthUser currentUser, HttpServletRequest request) {
        if (currentUser != null) {
            return "user:" + currentUser.id();
        }

        if (request == null) {
            return "anonymous";
        }

        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String ip = forwarded.split(",")[0].trim();
            return ip.isBlank() ? "anonymous" : "ip:" + ip;
        }

        String remoteAddr = request.getRemoteAddr();
        return remoteAddr == null || remoteAddr.isBlank() ? "anonymous" : "ip:" + remoteAddr;
    }
}
