package io.routepickapi.controller;

import io.routepickapi.dto.comment.CommentCreateRequest;
import io.routepickapi.dto.comment.CommentResponse;
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
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
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
    public ResponseEntity<IdResponse> createRoot(
        @Parameter(description = "게시글 ID")
        @PathVariable(name = "postId") @Min(1) Long postId,
        @Valid @RequestBody CommentCreateRequest req
    ) {
        Long id = commentService.createRoot(postId, req);
        log.info("Root comment created: postId={}, commentId={}", postId, id);
        return ResponseEntity.created(URI.create("/comments/" + id))
            .body(new IdResponse(id));
    }

    @Operation(summary = "대댓글 생성", description = "특정 게시글 내 부모 댓글에 대댓글 생성")
    @PostMapping("/{postId}/comments/{parentId}")
    public ResponseEntity<IdResponse> createReply(
        @Parameter(description = "게시글 ID")
        @PathVariable(name = "postId") @Min(1) Long postId,
        @Parameter(description = "부모 댓글 ID")
        @PathVariable(name = "parentId") @Min(1) Long parentId,
        @Valid @RequestBody CommentCreateRequest req
    ) {
        Long id = commentService.createReply(postId, parentId, req);
        log.info("Reply created: postId={}, parentId={}, commentId={}", postId, parentId, id);
        return ResponseEntity.created(URI.create("/comments/" + id))
            .body(new IdResponse(id));
    }

    @Operation(summary = "댓글 목록 조회", description = "본댓글 페이징(최신순) + 각 본댓글의 대댓글(작성시간 오름차순) 포함")
    @GetMapping("/{postId}/comments")
    public Page<CommentResponse> list(
        @Parameter(description = "게시글 ID")
        @PathVariable(name = "postId") @Min(1) Long postId,
        @ParameterObject @PageableDefault(size = 20) Pageable pageable
    ) {
        log.debug("GET /posts/{}/comments page={}, size={}", postId, pageable.getPageNumber(), pageable.getPageSize());
        return commentService.listRootsWithReplies(postId, pageable);
    }

    public record IdResponse(Long id) {}
}
