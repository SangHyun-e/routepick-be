package io.routepickapi.controller;

import io.routepickapi.common.error.CustomException;
import io.routepickapi.common.error.ErrorType;
import io.routepickapi.dto.user.AdminUserDetailResponse;
import io.routepickapi.dto.user.AdminUserListItemResponse;
import io.routepickapi.dto.user.AdminUserNicknameUpdateRequest;
import io.routepickapi.dto.user.AdminUserRejoinRestrictionLockByEmailRequest;
import io.routepickapi.dto.user.AdminUserRejoinRestrictionReleaseByEmailRequest;
import io.routepickapi.dto.user.AdminUserRejoinRestrictionReleaseRequest;
import io.routepickapi.dto.user.AdminUserStatusHistoryResponse;
import io.routepickapi.dto.user.AdminUserStatusUpdateRequest;
import io.routepickapi.entity.user.UserStatus;
import io.routepickapi.security.AuthUser;
import io.routepickapi.service.AdminUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/users")
@Validated
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final AdminUserService adminUserService;

    @Operation(summary = "관리자 사용자 목록", description = "관리자용 사용자 목록 조회")
    @GetMapping
    public Page<AdminUserListItemResponse> list(
        @RequestParam(required = false)
        @Schema(description = "이메일/닉네임 검색어")
        String q,
        @RequestParam(required = false)
        @Schema(description = "사용자 상태", example = "ACTIVE")
        String status,
        @ParameterObject @PageableDefault(size = 20) Pageable pageable
    ) {
        return adminUserService.list(q, parseStatus(status), pageable);
    }

    @Operation(summary = "관리자 사용자 상세", description = "관리자용 사용자 상세 조회")
    @GetMapping("/{id}")
    public AdminUserDetailResponse detail(@PathVariable @Min(1) Long id) {
        return adminUserService.getDetail(id);
    }

    @Operation(summary = "관리자 사용자 상태 변경", description = "관리자용 사용자 상태 변경")
    @PatchMapping("/{id}/status")
    public ResponseEntity<Void> updateStatus(
        @PathVariable @Min(1) Long id,
        @Valid @RequestBody AdminUserStatusUpdateRequest request,
        @AuthenticationPrincipal AuthUser adminUser
    ) {
        if (adminUser == null) {
            throw new CustomException(ErrorType.COMMON_UNAUTHORIZED);
        }
        adminUserService.updateStatus(id, request.status(), request.reason(), adminUser.id());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "관리자 사용자 닉네임 변경", description = "관리자용 사용자 닉네임 변경")
    @PatchMapping("/{id}/nickname")
    public ResponseEntity<Void> updateNickname(
        @PathVariable @Min(1) Long id,
        @Valid @RequestBody AdminUserNicknameUpdateRequest request,
        @AuthenticationPrincipal AuthUser adminUser
    ) {
        if (adminUser == null) {
            throw new CustomException(ErrorType.COMMON_UNAUTHORIZED);
        }
        adminUserService.updateNickname(id, request.nickname(), request.reason(), adminUser.id());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "관리자 사용자 재가입 제한 해제", description = "관리자용 재가입 제한 해제")
    @PatchMapping("/{id}/rejoin-restriction/release")
    public ResponseEntity<Void> releaseRejoinRestriction(
        @PathVariable @Min(1) Long id,
        @Valid @RequestBody(required = false) AdminUserRejoinRestrictionReleaseRequest request,
        @AuthenticationPrincipal AuthUser adminUser
    ) {
        if (adminUser == null) {
            throw new CustomException(ErrorType.COMMON_UNAUTHORIZED);
        }
        String reason = request != null ? request.reason() : null;
        adminUserService.releaseRejoinRestriction(id, adminUser.id(), reason);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "관리자 이메일 기반 재가입 제한 해제", description = "이메일로 재가입 제한 해제")
    @PatchMapping("/rejoin-restriction/release-by-email")
    public ResponseEntity<Void> releaseRejoinRestrictionByEmail(
        @Valid @RequestBody AdminUserRejoinRestrictionReleaseByEmailRequest request,
        @AuthenticationPrincipal AuthUser adminUser
    ) {
        if (adminUser == null) {
            throw new CustomException(ErrorType.COMMON_UNAUTHORIZED);
        }
        adminUserService.releaseRejoinRestrictionByEmail(request.email(), adminUser.id(),
            request.reason());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "관리자 사용자 재가입 제한 재설정", description = "사용자 재가입 제한 재설정")
    @PatchMapping("/{id}/rejoin-restriction/lock")
    public ResponseEntity<Void> lockRejoinRestriction(
        @PathVariable @Min(1) Long id,
        @AuthenticationPrincipal AuthUser adminUser
    ) {
        if (adminUser == null) {
            throw new CustomException(ErrorType.COMMON_UNAUTHORIZED);
        }
        adminUserService.lockRejoinRestriction(id, adminUser.id());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "관리자 이메일 기반 재가입 제한 재설정", description = "이메일로 재가입 제한 재설정")
    @PatchMapping("/rejoin-restriction/lock-by-email")
    public ResponseEntity<Void> lockRejoinRestrictionByEmail(
        @Valid @RequestBody AdminUserRejoinRestrictionLockByEmailRequest request,
        @AuthenticationPrincipal AuthUser adminUser
    ) {
        if (adminUser == null) {
            throw new CustomException(ErrorType.COMMON_UNAUTHORIZED);
        }
        adminUserService.lockRejoinRestrictionByEmail(request.email(), adminUser.id());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "관리자 사용자 상태 변경 이력", description = "관리자용 사용자 상태 변경 이력 조회")
    @GetMapping("/{id}/status-history")
    public Page<AdminUserStatusHistoryResponse> statusHistory(
        @PathVariable @Min(1) Long id,
        @ParameterObject @PageableDefault(size = 20) Pageable pageable
    ) {
        return adminUserService.getStatusHistory(id, pageable);
    }

    private UserStatus parseStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        if ("ALL".equals(normalized)) {
            return null;
        }
        try {
            return UserStatus.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            throw new CustomException(ErrorType.COMMON_INVALID_INPUT, "status 값이 올바르지 않습니다.");
        }
    }
}
