package io.routepickapi.controller;

import io.routepickapi.dto.post.PostCreateRequest;
import io.routepickapi.dto.post.PostListItemResponse;
import io.routepickapi.dto.post.PostResponse;
import io.routepickapi.dto.post.PostUpdateRequest;
import io.routepickapi.service.PostService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
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
import org.springframework.web.bind.annotation.RequestHeader;
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
    @Operation(summary = "게시글 생성", description = "title/content 필수, 선택적으로 region/좌표/tags 지정 가능, X-User-Id 헤더로 작성자 임시 지정")
    // Swagger에 요약/설명 등 추가
    @PostMapping
    public ResponseEntity<PostResponse> create(
        @Parameter(name = "X-User-Id", in = ParameterIn.HEADER, required = false, description = "임시 작성자 ID(테스트용)", example = "1")
        @RequestHeader(value = "X-User-Id", required = false) Long userId,
        @Valid @RequestBody PostCreateRequest req) {

        log.debug("POST /posts - userId={}, title='{}', region='{}' tags={}", userId, req.title(),
            req.region(), req.tags());
        Long id = postService.create(req, userId);
        PostResponse body = postService.getDetail(id, false); // 생성 직후 본분 반환(조회수 증가 X)

        log.info("Post Created: id={}, authorId={}", id, userId);

        return ResponseEntity.created(URI.create("/posts/" + id)).body(body);
    }

    // 게시글 목록 조회 API (조회순)
    @Operation(summary = "게시글 목록 조회(최신순)", description = "region 파라미터로 지역 필터 가능. 기본 페이지 크기 20")
    @GetMapping
    public Page<PostListItemResponse> list(
        @Parameter(description = "지역명 필터(예: 서울 성수동)") @RequestParam(required = false) String region,
        @ParameterObject @PageableDefault(size = 20) Pageable pageable
    ) {
        log.debug("GET /posts - region='{}', page={}, size={}", region, pageable.getPageNumber(),
            pageable.getPageSize());
        return postService.list(region, pageable);
    }

    // 게시글 상세 조회 API
    @Operation(summary = "게시글 상세 조회", description = "기본적으로 조회수 +1, incView=false로 비활성화 가능")
    @GetMapping("/{id}")
    public PostResponse detail(
        @Parameter(description = "게시글 ID")
        @PathVariable(name = "id") @Min(1) Long id,
        @Parameter(description = "조회수 증가 여부", example = "true")
        @RequestParam(name = "incView", defaultValue = "true") boolean incView
    ) {
        log.debug("GET /posts/{} - incView={}", id, incView);
        return postService.getDetail(id, incView);
    }

    // 좋아요+1 API
    @Operation(summary = "좋아요 +1", description = "현재 누적 좋아요 수를 반환")
    @PostMapping("/{id}/like")
    public LikeResponse like(
        @Parameter(description = "게시글 ID")
        @PathVariable(name = "id") @Min(1) Long id) {
        int likeCount = postService.like(id);
        log.info("Post Liked: id={}, likeCount={}", id, likeCount);
        return new LikeResponse(likeCount);
    }

    // 소프트 삭제
    @Operation(summary = "게시글 소프트 삭제", description = "status=DELETED로 변환(물리삭제 X)")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> softDelete(
        @Parameter(description = "게시글 ID")
        @PathVariable(name = "id") @Min(1) Long id) {
        postService.softDelete(id);
        log.info("Post Soft-Deleted: id={}", id);
        return ResponseEntity.noContent().build(); // 반환값 204로 수정
    }

    // 게시글 활성화
    @Operation(summary = "게시글 활성화", description = "status=ACTIVE로 전환")
    @PatchMapping("/{id}/activate")
    public ApiMessage activate(
        @Parameter(description = "게시글 ID")
        @PathVariable(name = "id") @Min(1) Long id) {
        postService.activate(id);
        log.info("Post Activated: id={}", id);
        return new ApiMessage("activated");
    }

    /**
     * 게시글 검색 API
     * - region(선택): 지역명 완전일치
     * - keyword(선택): 제목/내용 부분일치(대소문자 무시)
     * - pageable: page, size, sort(예: sort=createdAt.desc)
     * - 반환: 목록 화면용 경량 DTO(Page)
     */
    @Operation(
        summary = "게시글 검색",
        description = "region(선택), keyword(선택)로 검색. 기본 정렬은 createdAt DESC"
    )
    @GetMapping("/search")
    public Page<PostListItemResponse> search(
        @Parameter(description = "지역명(완전일치)") @RequestParam(required = false) String region,
        @Parameter(description = "키워드(제목/본문에 포함)") @RequestParam(required = false) String keyword,
        @ParameterObject @PageableDefault(size = 20) Pageable pageable
    ) {
        log.debug("GET /posts/search - region='{}', keyword='{}', page={}, size={}",
            region, keyword, pageable.getPageNumber(), pageable.getPageSize());
        return postService.search(region, keyword, pageable);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "게시글 수정", description = "전달된 필드만 변경하고, 수정된 게시글 200 반환")
    public ResponseEntity<PostResponse> update(
        @Parameter(description = "게시글 ID") @PathVariable(name = "id") @Min(1) Long id,
        @Valid @RequestBody PostUpdateRequest req
    ) {
        log.debug(
            "PATCH /posts/{} - title?={}, content?={}, region?={}, lat?={}, lon?={}, tags?={}",
            id,
            req.title() != null, req.content() != null, req.region() != null,
            req.latitude() != null, req.longitude() != null, req.tags() != null);

        PostResponse body = postService.update(id, req);

        log.info("Post Updated: id={}", id);
        return ResponseEntity.ok(body);
    }


    public record LikeResponse(int likeCount) {

    }

    public record ApiMessage(String message) {

    }
}