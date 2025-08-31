package io.routepickapi.controller;

import io.routepickapi.dto.post.PostCreateRequest;
import io.routepickapi.dto.post.PostListItemResponse;
import io.routepickapi.dto.post.PostResponse;
import io.routepickapi.service.PostService;
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

    // 게시글 생성 API
    @Operation(summary = "게시글 생성", description = "title/content 필수, 선택적으로 region/좌표/tags 지정 가능") // Swagger에 요약/설명 등 추가
    @PostMapping
    public ResponseEntity<PostResponse> create(@Valid @RequestBody PostCreateRequest req) {

        log.debug("POST /posts - title='{}';, region='{}' tags={}", req.title(), req.region(), req.tags());
        Long id = postService.create(req);
        PostResponse body = postService.getDetail(id, false); // 생성 직후 본분 반환(조회수 증가 X)

        log.info("Post Created: id={}", id);

        return ResponseEntity.created(URI.create("/posts/" + id)).body(body);
    }

    // 게시글 목록 조회 API (조회순)
    @Operation(summary = "게시글 목록 조회(최신순)", description = "region 파라미터로 지역 필터 가능. 기본 페이지 크기 20")
    @GetMapping
    public Page<PostListItemResponse> list(
        @Parameter(description = "지역명 필터(예: 서울 성수동)") @RequestParam(required = false) String region,
        @ParameterObject @PageableDefault(size = 20) Pageable pageable
    ) {
        log.debug("GET /posts - region='{}', page={}, size={}", region, pageable.getPageNumber(), pageable.getPageSize());
        return postService.list(region, pageable);
    }

    // 게시글 상세 조회 API
    @Operation(summary = "게시글 상세 조회", description = "기본적으로 조회수 +1, incView=false로 비활성화 가능")
    @GetMapping("/{id}")
    public PostResponse detail(
        @Parameter(description = "게시글 ID")
        @PathVariable(name="id") @Min(1) Long id,
        @Parameter(description = "조회수 증가 여부", example = "true")
        @RequestParam(name ="incView", defaultValue = "true") boolean incView
    ) {
        log.debug("GET /posts/{} - incView={}", id, incView);
        return postService.getDetail(id, incView);
    }

    // 좋아요+1 API
    @Operation(summary = "좋아요 +1", description = "현재 누적 좋아요 수를 반환")
    @PostMapping("/{id}/like")
    public LikeResponse like(
        @Parameter(description = "게시글 ID")
        @PathVariable(name="id") @Min(1) Long id) {
        int likeCount = postService.like(id);
        log.info("Post Liked: id={}, likeCount={}", id, likeCount);
        return new LikeResponse(likeCount);
    }

    // 소프트 삭제
    @Operation(summary = "게시글 소프트 삭제", description = "status=DELETED로 변환(물리삭제 X)")
    @DeleteMapping("/{id}")
    public ApiMessage softDelete(
        @Parameter(description = "게시글 ID")
        @PathVariable(name="id") @Min(1) Long id) {
        postService.softDelete(id);
        log.info("Post Soft-Deleted: id={}", id);
        return new ApiMessage("deleted");
    }

    // 게시글 활성화
    @Operation(summary = "게시글 활성화", description = "status=ACTIVE로 전환")
    @PatchMapping("/{id}/activate")
    public ApiMessage activate(
        @Parameter(description = "게시글 ID")
        @PathVariable(name="id") @Min(1) Long id) {
        postService.activate(id);
        log.info("Post Activated: id={}", id);
        return new ApiMessage("activated");
    }

    public record LikeResponse(int likeCount) {}
    public record ApiMessage(String message) {}
}
