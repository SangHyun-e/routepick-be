package io.routepickapi.controller;

import io.routepickapi.dto.post.PostListItemResponse;
import io.routepickapi.entity.post.PostStatus;
import io.routepickapi.service.PostService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.Locale;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/posts")
@Validated
@PreAuthorize("hasRole('ADMIN')")
public class AdminPostController {

    private final PostService postService;

    @Operation(summary = "관리자 게시글 목록", description = "상태별 게시글 목록 조회")
    @GetMapping
    public Page<PostListItemResponse> list(
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String region,
        @RequestParam(required = false) String keyword,
        @ParameterObject @PageableDefault(size = 20) Pageable pageable
    ) {
        PostStatus parsed = parseStatus(status);
        return postService.listForAdmin(parsed, region, keyword, pageable);
    }

    @Operation(summary = "관리자 게시글 상태 조회", description = "상태 확인용 최소 응답")
    @GetMapping("/{id}/status")
    public ResponseEntity<StatusResponse> status(@PathVariable @Min(1) Long id) {
        PostStatus status = postService.findStatus(id);
        return ResponseEntity.ok(new StatusResponse(status));
    }

    @Operation(summary = "관리자 게시글 비활성화", description = "게시글 상태를 숨김 처리")
    @PatchMapping("/{id}/hide")
    public ResponseEntity<Void> hide(@PathVariable @Min(1) Long id) {
        postService.hideByAdmin(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "관리자 게시글 활성화", description = "게시글 상태를 활성 처리")
    @PatchMapping("/{id}/activate")
    public ResponseEntity<Void> activate(@PathVariable @Min(1) Long id) {
        postService.activateByAdmin(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "관리자 공지 등록", description = "게시글 공지 여부 설정")
    @PatchMapping("/{id}/notice")
    public ResponseEntity<Void> updateNotice(
        @PathVariable @Min(1) Long id,
        @RequestBody NoticeRequest request
    ) {
        postService.updateNoticeByAdmin(id, request.isNotice());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "관리자 공지 고정 토글", description = "공지 게시글 고정 여부 토글")
    @PatchMapping("/{id}/notice/pin")
    public ResponseEntity<Void> toggleNoticePinned(@PathVariable @Min(1) Long id) {
        postService.toggleNoticePinnedByAdmin(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "관리자 게시글 물리 삭제", description = "게시글을 DB에서 삭제")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> hardDelete(@PathVariable @Min(1) Long id) {
        postService.hardDeleteByAdmin(id);
        return ResponseEntity.noContent().build();
    }

    private PostStatus parseStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        if ("ALL".equals(normalized)) {
            return null;
        }
        try {
            return PostStatus.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            throw new io.routepickapi.common.error.CustomException(
                io.routepickapi.common.error.ErrorType.COMMON_INVALID_INPUT,
                "status 값이 올바르지 않습니다."
            );
        }
    }

    public record StatusResponse(PostStatus status) {

    }

    public record NoticeRequest(
        @NotNull
        @Schema(description = "공지 여부", example = "true")
        Boolean isNotice
    ) {

    }
}
