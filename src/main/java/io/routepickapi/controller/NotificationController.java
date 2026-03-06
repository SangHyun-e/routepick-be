package io.routepickapi.controller;

import io.routepickapi.dto.notification.NotificationResponse;
import io.routepickapi.security.AuthUser;
import io.routepickapi.service.NotificationService;
import io.routepickapi.service.RealtimeStreamService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/notifications")
@PreAuthorize("isAuthenticated()")
public class NotificationController {

    private final NotificationService notificationService;
    private final RealtimeStreamService realtimeStreamService;

    @Operation(summary = "내 알림 목록", description = "내 알림 목록을 최신순으로 조회합니다.",
        security = {@SecurityRequirement(name = "bearerAuth")})
    @GetMapping
    public Page<NotificationResponse> list(
        @AuthenticationPrincipal AuthUser currentUser,
        @RequestParam(required = false)
        @Parameter(description = "읽음 여부 필터", example = "false")
        Boolean read,
        @ParameterObject
        @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
        Pageable pageable
    ) {
        log.info("GET /notifications - userId={}, read={}", currentUser.id(), read);
        return notificationService.list(currentUser.id(), read, pageable);
    }

    @Operation(summary = "알림 실시간 스트림", description = "내 알림 이벤트를 SSE로 전달합니다.",
        security = {@SecurityRequirement(name = "bearerAuth")})
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@AuthenticationPrincipal AuthUser currentUser) {
        return realtimeStreamService.subscribeNotifications(currentUser.id());
    }

    @Operation(summary = "알림 읽음 처리", description = "알림을 읽음으로 처리합니다.",
        security = {@SecurityRequirement(name = "bearerAuth")})
    @PatchMapping("/{id}/read")
    public ResponseEntity<Void> read(
        @AuthenticationPrincipal AuthUser currentUser,
        @PathVariable @Min(1) Long id
    ) {
        notificationService.markRead(currentUser.id(), id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "알림 전체 읽음 처리", description = "모든 알림을 읽음 처리합니다.",
        security = {@SecurityRequirement(name = "bearerAuth")})
    @PatchMapping("/read-all")
    public ResponseEntity<Void> readAll(@AuthenticationPrincipal AuthUser currentUser) {
        notificationService.markAllRead(currentUser.id());
        return ResponseEntity.noContent().build();
    }
}
