package io.routepickapi.service;

import io.routepickapi.common.error.CustomException;
import io.routepickapi.common.error.ErrorType;
import io.routepickapi.dto.post.PostCreateRequest;
import io.routepickapi.dto.post.PostListItemResponse;
import io.routepickapi.dto.post.PostResponse;
import io.routepickapi.dto.post.PostUpdateRequest;
import io.routepickapi.entity.post.Post;
import io.routepickapi.entity.post.PostStatus;
import io.routepickapi.entity.user.User;
import io.routepickapi.entity.user.UserStatus;
import io.routepickapi.repository.PostQueryRepository;
import io.routepickapi.repository.PostRepository;
import io.routepickapi.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PostService {

    private final PostRepository postRepository;
    private final PostQueryRepository postQueryRepository; // 동적 검색용 QueryDSL 리포지토리
    private final UserRepository userRepository;

    public Long create(PostCreateRequest req, Long currentUserId) {
        Post post = new Post(req.title(), req.content());
        if (req.latitude() != null || req.longitude() != null) {
            post.setCoordinates(req.latitude(), req.longitude());
        }

        if (req.region() != null && !req.region().isBlank()) {
            post.setRegion(req.region());
        }
        post.setTags(req.tags());

        if (currentUserId != null) {
            User author = userRepository.findByIdAndStatus(currentUserId, UserStatus.ACTIVE)
                .orElseThrow(
                    () -> new CustomException(ErrorType.COMMON_UNAUTHORIZED));
            post.setAuthor(author);
        }

        Long id = postRepository.save(post).getId();
        log.info("Create Post: id={}, title='{}', region='{}', authorId={}", id, post.getTitle(),
            post.getRegion(), (post.getAuthor() != null ? post.getAuthor().getId() : null));
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
        // 1) 존재/상태 확인 + 태그 fetch join
        Post post = postRepository.findWithTagsById(id)
            .orElseThrow(() -> new CustomException(ErrorType.POST_NOT_FOUND));

        if (post.getStatus() != PostStatus.ACTIVE) {
            throw new CustomException(ErrorType.POST_NOT_FOUND);
        }

        // 2) 조회수 증가를 DB에서 원자적으로 처리
        if (increaseView) {
            int updated = postRepository.incrementViewCount(id, PostStatus.ACTIVE);
            if (updated == 0) {
                // ACTIVE 가 아닌 상태로 바뀌었을 가능성 방어
                throw new CustomException(ErrorType.POST_NOT_FOUND);
            }
            // 현재 영속성 컨텍스트의 post 는 아직 예전 viewCount 이므로 화면 일관성을 위해 메모리도 +1
            post.increaseView();
            log.debug("Increase View (atomic): id={}, newViewCount={}", id, post.getViewCount());
        }
        return PostResponse.from(post);
    }

    public int like(Long id) {
        log.debug("POST /posts/{}/like - request received", id);
        // DB 에서 원자적으로 +1
        int updated = postRepository.incrementLikeCount(id, PostStatus.ACTIVE);
        if (updated == 0) {
            throw new CustomException(ErrorType.POST_NOT_FOUND);
        }

        // 최신 값을 정확히 반환하려면 재조회해서 꺼내는게 안전(동시성 고려)
        int likeCount = postRepository.findByIdAndStatus(id, PostStatus.ACTIVE)
            .map(Post::getLikeCount)
            .orElseThrow(() -> {
                log.error("Like increased but reload failed (id={})", id);
                return new CustomException(ErrorType.POST_NOT_FOUND);
            });
        log.info("Post liked: id={}, likeCount={}", id, likeCount);
        return likeCount;
    }

    @PreAuthorize("@authz.isPostOwner(#id) or hasRole('ADMIN')")
    public void softDelete(Long id) {
        Post post = postRepository.findById(id)
            .orElseThrow(() -> new CustomException(ErrorType.POST_NOT_FOUND));
        post.softDelete();
        log.info("Soft Delete: id={}", id);
    }

    @PreAuthorize("@authz.isPostOwner(#id) or hasRole('ADMIN')")
    public void activate(Long id) {
        Post post = postRepository.findById(id)
            .orElseThrow(() -> new CustomException(ErrorType.POST_NOT_FOUND));
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

    @PreAuthorize("@authz.isPostOwner(#id) or hasRole('ADMIN')")
    public PostResponse update(Long id, PostUpdateRequest req) {
        // DELETED 는 수정 불가, ACTIVE/HIDDEN 은 수정 허용
        Post post = postRepository.findById(id)
            .orElseThrow(() -> new CustomException(ErrorType.POST_NOT_FOUND));

        if (post.getStatus() == PostStatus.DELETED) {
            throw new CustomException(ErrorType.POST_NOT_FOUND);
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
