package io.routepickapi.controller;

import io.routepickapi.dto.post.PostCreateRequest;
import io.routepickapi.dto.post.PostListItemResponse;
import io.routepickapi.dto.post.PostResponse;
import io.routepickapi.dto.post.PostUpdateRequest;
import io.routepickapi.security.AuthUser;
import io.routepickapi.service.PostService;
import io.swagger.v3.oas.annotations.Operation;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/posts")
@Validated
public class PostController {

    private final PostService postService;

    @Operation(summary = "게시글 생성", description = "JWT 인증 필요")
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PostResponse> create(
        @AuthenticationPrincipal AuthUser currentUser,
        @Valid @RequestBody PostCreateRequest req
    ) {
        PostResponse body = postService.create(req, currentUser.id());
        return ResponseEntity.created(URI.create("/posts/" + body.id())).body(body);
    }

    @Operation(summary = "게시글 목록 조회(최신순)", description = "비로그인은 isLikedByCurrentUser=null")
    @GetMapping
    public Page<PostListItemResponse> list(
        @RequestParam(required = false) String region,
        @ParameterObject @PageableDefault(size = 20) Pageable pageable,
        @AuthenticationPrincipal AuthUser currentUser
    ) {
        Long currentUserId = (currentUser != null) ? currentUser.id() : null;
        return postService.list(region, pageable, currentUserId);
    }

    @Operation(summary = "게시글 상세 조회", description = "기본 조회수 +1")
    @GetMapping("/{id:\\d+}")
    public PostResponse detail(
        @PathVariable(name = "id") @Min(1) Long id,
        @RequestParam(name = "incView", defaultValue = "true") boolean incView,
        @AuthenticationPrincipal AuthUser currentUser
    ) {
        Long currentUserId = (currentUser != null) ? currentUser.id() : null;
        return postService.getDetail(id, incView, currentUserId,
            currentUser != null ? currentUser.role() : null);
    }

    @Operation(summary = "게시글 검색", description = "비로그인은 isLikedByCurrentUser=null")
    @GetMapping("/search")
    public Page<PostListItemResponse> search(
        @RequestParam(required = false) String region,
        @RequestParam(required = false) String keyword,
        @ParameterObject @PageableDefault(size = 20) Pageable pageable,
        @AuthenticationPrincipal AuthUser currentUser
    ) {
        Long currentUserId = (currentUser != null) ? currentUser.id() : null;
        return postService.search(region, keyword, pageable, currentUserId);
    }

    @Operation(summary = "게시글 수정", description = "전달된 필드만 변경")
    @PatchMapping("/{id:\\d+}")
    public ResponseEntity<PostResponse> update(
        @PathVariable(name = "id") @Min(1) Long id,
        @Valid @RequestBody PostUpdateRequest req,
        @AuthenticationPrincipal AuthUser currentUser
    ) {
        Long currentUserId = (currentUser != null) ? currentUser.id() : null;
        PostResponse body = postService.update(id, req, currentUserId);
        return ResponseEntity.ok(body);
    }

    @Operation(summary = "좋아요 토글", description = "JWT 인증 필요")
    @PostMapping("/{id:\\d+}/like")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<LikeResponse> toggleLike(
        @PathVariable(name = "id") @Min(1) Long id,
        @AuthenticationPrincipal AuthUser currentUser
    ) {
        LikeResponse response = postService.toggleLike(id, currentUser.id());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "스크랩 토글", description = "JWT 인증 필요")
    @PostMapping("/{id:\\d+}/scrap")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ScrapResponse> toggleScrap(
        @PathVariable(name = "id") @Min(1) Long id,
        @AuthenticationPrincipal AuthUser currentUser
    ) {
        ScrapResponse response = postService.toggleScrap(id, currentUser.id());
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id:\\d+}")
    public ResponseEntity<Void> softDelete(@PathVariable(name = "id") @Min(1) Long id) {
        postService.softDelete(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id:\\d+}/hide")
    public ApiMessage hide(@PathVariable(name = "id") @Min(1) Long id) {
        postService.hide(id);
        return new ApiMessage("hidden");
    }

    @PatchMapping("/{id:\\d+}/activate")
    public ApiMessage activate(@PathVariable(name = "id") @Min(1) Long id) {
        postService.activate(id);
        return new ApiMessage("activated");
    }

    public record LikeResponse(int likeCount) {

    }

    public record ScrapResponse(boolean scrapped) {

    }

    public record ApiMessage(String message) {

    }
}
