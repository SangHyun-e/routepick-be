package io.routepickapi.controller;

import io.routepickapi.dto.course.CourseExplainResponse;
import io.routepickapi.security.AuthUser;
import io.routepickapi.service.CourseExplainService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/courses")
public class CourseExplainController {

    private final CourseExplainService courseExplainService;

    @Operation(summary = "코스 AI 설명", description = "저장된 추천 코스를 AI로 설명합니다.",
        security = {@SecurityRequirement(name = "bearerAuth")})
    @PostMapping("/{courseId}/explain")
    @PreAuthorize("isAuthenticated()")
    public CourseExplainResponse explain(
        @PathVariable @Min(1) Long courseId,
        @AuthenticationPrincipal AuthUser currentUser
    ) {
        log.info("POST /api/courses/{}/explain - userId={}", courseId, currentUser.id());
        return courseExplainService.explain(courseId, currentUser.id());
    }
}
