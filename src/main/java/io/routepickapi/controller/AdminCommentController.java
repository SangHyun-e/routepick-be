package io.routepickapi.controller;

import io.routepickapi.dto.comment.AdminCommentListItemResponse;
import io.routepickapi.service.AdminCommentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@Validated
@RestController
@RequestMapping("/admin/comments")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminCommentController {

    private final AdminCommentService adminCommentService;

    @Operation(
        summary = "관리자 댓글 목록",
        description = "댓글 상태/키워드로 목록을 조회합니다.",
        security = {@SecurityRequirement(name = "bearerAuth")}
    )
    @GetMapping
    public Page<AdminCommentListItemResponse> list(
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String keyword,
        @ParameterObject @PageableDefault(size = 20) Pageable pageable
    ) {
        log.info("GET /admin/comments status={}, keyword={}", status, keyword);
        return adminCommentService.list(status, keyword, pageable);
    }

    @Operation(
        summary = "댓글 숨김(관리자)",
        description = "관리자가 댓글을 소프트 삭제 처리합니다.",
        security = {@SecurityRequirement(name = "bearerAuth")}
    )
    @PatchMapping("/{commentId}/hide")
    public ResponseEntity<Void> hide(@PathVariable @Min(1) Long commentId) {
        adminCommentService.softDelete(commentId);
        return ResponseEntity.noContent().build();
    }

    @Operation(
        summary = "댓글 활성화(관리자)",
        description = "관리자가 삭제된 댓글을 복구합니다.",
        security = {@SecurityRequirement(name = "bearerAuth")}
    )
    @PatchMapping("/{commentId}/activate")
    public ResponseEntity<Void> activate(@PathVariable @Min(1) Long commentId) {
        adminCommentService.activate(commentId);
        return ResponseEntity.noContent().build();
    }

    @Operation(
        summary = "댓글 물리 삭제(관리자)",
        description = "관리자가 댓글을 물리 삭제합니다.",
        security = {@SecurityRequirement(name = "bearerAuth")}
    )
    @DeleteMapping("/{commentId}")
    public ResponseEntity<Void> hardDelete(@PathVariable @Min(1) Long commentId) {
        adminCommentService.hardDelete(commentId);
        return ResponseEntity.noContent().build();
    }
}
