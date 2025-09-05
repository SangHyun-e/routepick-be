package io.routepickapi.service;

import io.routepickapi.dto.post.PostCreateRequest;
import io.routepickapi.dto.post.PostListItemResponse;
import io.routepickapi.dto.post.PostResponse;
import io.routepickapi.dto.post.PostUpdateRequest;
import io.routepickapi.entity.post.Post;
import io.routepickapi.entity.post.PostStatus;
import io.routepickapi.repository.PostQueryRepository;
import io.routepickapi.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PostService {

    private final PostRepository postRepository;
    private final PostQueryRepository postQueryRepository; // 동적 검색용 QueryDSL 리포지토리

    public Long create(PostCreateRequest req) {
        Post post = new Post(req.title(), req.content());
        if (req.latitude() != null || req.longitude() != null) {
            post.setCoordinates(req.latitude(), req.longitude());
        }

        if (req.region() != null && !req.region().isBlank()) {
            post.setRegion(req.region());
        }
        post.setTags(req.tags());

        Long id = postRepository.save(post).getId();
        log.info("Create Post: id={}, title='{}', region='{}'", id, post.getTitle(),
            post.getRegion());
        return id;
    }

    @Transactional(readOnly = true)
    public Page<PostListItemResponse> list(String region, Pageable pageable) {
        if (region == null || region.isBlank()) {
            return postRepository
                .findByStatusOrderByCreatedAtDesc(PostStatus.ACTIVE, pageable)
                .map(PostListItemResponse::from);
        }
        return postRepository
            .findByRegionAndStatusOrderByCreatedAtDesc(region, PostStatus.ACTIVE, pageable)
            .map(PostListItemResponse::from);
    }

    public PostResponse getDetail(Long id, boolean increaseView) {
        Post post = postRepository.findWithTagsById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "post not found"));

        if (post.getStatus() != PostStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "post not available");
        }

        if (increaseView) {
            post.increaseView();
            log.debug("Increase View: id={}, newViewCount={}", id, post.getViewCount());
        }
        return PostResponse.from(post);
    }

    public int like(Long id) {
        Post post = postRepository.findByIdAndStatus(id, PostStatus.ACTIVE)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "post not available"));
        post.increaseLike();
        log.debug("Increase Like: id={}, newLikeCount={}", id, post.getLikeCount());
        return post.getLikeCount();
    }

    public void softDelete(Long id) {
        Post post = postRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "post not found"));
        post.softDelete();
        log.info("Soft Delete: id={}", id);
    }

    public void activate(Long id) {
        Post post = postRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "post not found"));
        post.activated();
        log.info("Activate: id={}", id);
    }

    /**
     * 검색 서비스
     * - region(선택): 지역명 완전일치 필터
     * - keyword(선택): 제목/내용 부분일치(대소문자 무시)
     * - pageable: 페이지/정렬(기본 createdAt DESC)
     * - 반환: 목록 화면용 경량 DTO(page)
     */
    @Transactional(readOnly = true)
    public Page<PostListItemResponse> search(String region, String keyword, Pageable pageable) {
        Page<Post> page = postQueryRepository.searchByRegionAndKeyword(region, keyword, pageable);
        // 목록 응답은 Lazy 컬렉션(tags) 접근하지 않는 경량 DTO로 매핑 -> Lazy 예외 예방
        return page.map(PostListItemResponse::from);
    }

    public PostResponse update(Long id, PostUpdateRequest req) {
        // DELETED 는 수정 불가, ACTIVE/HIDDEN 은 수정 허용
        Post post = postRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "post not found"));

        if (post.getStatus() == PostStatus.DELETED) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "post not available");
        }

        // 전달된 필드만 변경 (null은 무시)
        if (req.title() != null) {
            post.changeTitle(req.title());
        }
        if (req.content() != null) {
            post.changeContent(req.content());
        }
        if (req.region() != null) {
            post.setRegion(req.region());
        }

        if (req.latitude() != null && req.longitude() != null) {
            post.setCoordinates(req.latitude(), req.longitude());
        }
        if (req.tags() != null) {
            post.setTags(req.tags());
        }

        log.info("Post Updated: id={}", id);

        // 수정된 최신 본문 반환
        return PostResponse.from(post);
    }
}
