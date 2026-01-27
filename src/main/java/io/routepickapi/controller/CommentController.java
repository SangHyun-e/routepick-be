package io.routepickapi.controller;

import io.routepickapi.dto.comment.CommentCreateRequest;
import io.routepickapi.dto.comment.CommentLikeToggleResponse;
import io.routepickapi.dto.comment.CommentResponse;
import io.routepickapi.dto.comment.CommentUpdateRequest;
import io.routepickapi.security.AuthUser;
import io.routepickapi.service.CommentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/posts")
@Validated
public class CommentController {

    private final CommentService commentService;

    @Operation(summary = "본 댓글 생성", description = "특정 게시글에 본댓글 생성")
    @PostMapping("/{postId}/comments")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<IdResponse> createRoot(
        @AuthenticationPrincipal AuthUser currentUser,
        @PathVariable(name = "postId") @Min(1) Long postId,
        @Valid @RequestBody CommentCreateRequest req
    ) {
        Long id = commentService.createRoot(postId, currentUser.id(), req);
        log.info("Root comment created: postId={}, commentId={}", postId, id);
        return ResponseEntity
            .created(URI.create(String.format("/posts/%d/comments/%d", postId, id)))
            .body(new IdResponse(id));
    }

    @Operation(summary = "대댓글 생성", description = "특정 게시글 내 부모 댓글에 대댓글 생성")
    @PostMapping("/{postId}/comments/{parentId}/replies")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<IdResponse> createReply(
        @AuthenticationPrincipal AuthUser currentUser,
        @PathVariable(name = "postId") @Min(1) Long postId,
        @Parameter(description = "부모 댓글 ID")
        @PathVariable(name = "parentId") @Min(1) Long parentId,
        @Valid @RequestBody CommentCreateRequest req
    ) {
        Long id = commentService.createReply(postId, parentId, currentUser.id(), req);
        log.info("Reply created: postId={}, parentId={}, commentId={}", postId, parentId, id);
        return ResponseEntity
            .created(URI.create(String.format("/posts/%d/comments/%d", postId, id)))
            .body(new IdResponse(id));
    }

    @Operation(summary = "댓글 목록 조회", description = "본댓글 페이징(최신순) + 각 본댓글의 대댓글(작성시간 오름차순) 포함")
    @GetMapping("/{postId}/comments")
    public Page<CommentResponse> list(
        @Parameter(description = "게시글 ID")
        @PathVariable(name = "postId") @Min(1) Long postId,
        @ParameterObject @PageableDefault(size = 20) Pageable pageable
    ) {
        log.debug("GET /posts/{}/comments page={}, size={}", postId, pageable.getPageNumber(),
            pageable.getPageSize());
        return commentService.listRootsWithReplies(postId, pageable);
    }

    @Operation(summary = "댓글 좋아요 토글", description = "특정 게시글 내 댓글의 좋아요 추가/취소, 현재 좋아요 수 반환 (JWT 인증 필요)")
    @PostMapping("/{postId}/comments/{commentId}/like")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CommentLikeToggleResponse> toggleLike(
        @AuthenticationPrincipal AuthUser currentUser,
        @Parameter(description = "게시글 ID")
        @PathVariable @Min(1) Long postId,
        @Parameter(description = "댓글 ID")
        @PathVariable @Min(1) Long commentId
    ) {
        CommentLikeToggleResponse res =
            commentService.toggleLike(postId, commentId, currentUser.id());

        log.info("Comment like toggled: postId={}. commentId={}, userId={}. liked={}, likeCount={}",
            postId, commentId, currentUser.id(), res.liked(), res.likeCount());

        return ResponseEntity.ok(res);
    }

    @Operation(summary = "댓글 삭제(소프트)", description = "특정 게시글 내 댓글을 소프트 삭제합니다.")
    @DeleteMapping("/{postId}/comments/{commentId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> delete(
        @Parameter(description = "게시글 ID") @PathVariable @Min(1) Long postId,
        @Parameter(description = "댓글 ID") @PathVariable @Min(1) Long commentId
    ) {
        log.debug("DELETE /posts/{}/comments/{} - request received", postId, commentId);

        commentService.softDelete(postId, commentId);

        log.info("Comment soft-deleted: postId={}, commentId={}", postId, commentId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "댓글 내용 수정", description = "ACTIVE 상태의 댓글 내용만 수정 가능")
    @PatchMapping("/{postId}/comments/{commentId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CommentResponse> update(
        @Parameter(description = "게시글 ID") @PathVariable @Min(1) Long postId,
        @Parameter(description = "댓글 ID") @PathVariable @Min(1) Long commentId,
        @Valid @RequestBody CommentUpdateRequest req
    ) {
        log.debug("PATCH /posts/{}/comments/{} - request received", postId, commentId);

        CommentResponse updated = commentService.updateContent(postId, commentId, req);

        log.info("Comment updated: postId={}, commentId={}", postId, commentId);
        return ResponseEntity.ok(updated); // 200 OK + 갱신된 리소스 반환
    }

    public record IdResponse(Long id) {

    }
    
}
