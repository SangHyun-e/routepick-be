package io.routepickapi.service;

import io.routepickapi.common.error.CustomException;
import io.routepickapi.common.error.ErrorType;
import io.routepickapi.controller.PostController.LikeResponse;
import io.routepickapi.dto.comment.CommentResponse;
import io.routepickapi.dto.post.PostCreateRequest;
import io.routepickapi.dto.post.PostListItemResponse;
import io.routepickapi.dto.post.PostResponse;
import io.routepickapi.dto.post.PostUpdateRequest;
import io.routepickapi.entity.comment.Comment;
import io.routepickapi.entity.comment.CommentStatus;
import io.routepickapi.entity.post.Post;
import io.routepickapi.entity.post.PostLike;
import io.routepickapi.entity.post.PostStatus;
import io.routepickapi.entity.user.User;
import io.routepickapi.entity.user.UserStatus;
import io.routepickapi.repository.CommentQueryRepository;
import io.routepickapi.repository.CommentRepository;
import io.routepickapi.repository.PostLikeRepository;
import io.routepickapi.repository.PostQueryRepository;
import io.routepickapi.repository.PostRepository;
import io.routepickapi.repository.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
    private final PostLikeRepository postLikeRepository;
    private final CommentQueryRepository commentQueryRepository;
    private final CommentRepository commentRepository;

    public PostResponse create(PostCreateRequest req, Long currentUserId) {
        Post post = new Post(req.title(), req.content());

        // 좌표는 둘 중 하나라도 있으면 setCoordinates
        if (req.latitude() != null || req.longitude() != null) {
            post.setCoordinates(req.latitude(), req.longitude());
        }

        // region: null/blank 면 저장하지 않음 (=null 유지)
        if (req.region() != null && !req.region().isBlank()) {
            post.setRegion(req.region());
        }

        post.setTags(req.tags());

        // 작성자 지정 (인증 필요)
        User author = requireActiveUser(currentUserId);
        post.setAuthor(author);

        Post saved = postRepository.save(post);
        log.info("Create Post: id={}, title='{}', region='{}', authorId={}",
            saved.getId(), saved.getTitle(), saved.getRegion(), author.getId());

        // 생성 직후에는 좋아요 false
        return PostResponse.from(saved, false);
    }

    @Transactional(readOnly = true)
    public Page<PostListItemResponse> list(String region, Pageable pageable, Long currentUserId) {
        Page<Post> posts;
        if (region == null || region.isBlank()) {
            posts = postQueryRepository.searchByRegionAndKeyword(null, null, pageable);
        } else {
            posts = postRepository.findByRegionAndStatusOrderByCreatedAtDesc(region,
                PostStatus.ACTIVE, pageable);
        }

        // 현재 페이지의 postIds
        List<Long> postIds = posts.getContent().stream()
            .map(Post::getId)
            .toList();

        // 댓글 수 집계 (ACTIVE만 카운트)
        Map<Long, Integer> commentCountMap =
            commentQueryRepository.countByPostIds(postIds, CommentStatus.ACTIVE);

        // 비로그인 사용자
        if (currentUserId == null) {
            return posts.map(p -> {
                int commentCount = commentCountMap.getOrDefault(p.getId(), 0);
                return PostListItemResponse.from(p, commentCount);
            });
        }

        // 로그인 사용자 - 좋아요 상태 일괄 조회 (N+1 방지)
        Set<Long> likedPostIds = postLikeRepository.findLikedPostIdsByUserIdAndPostIds(postIds,
            currentUserId);

        return posts.map(p -> {
            boolean liked = likedPostIds.contains(p.getId());
            int commentCount = commentCountMap.getOrDefault(p.getId(), 0);
            return PostListItemResponse.from(p, liked, commentCount);
        });
    }

    @Transactional(readOnly = true)
    public Page<PostListItemResponse> listForAdmin(
        PostStatus status,
        String region,
        String keyword,
        Pageable pageable
    ) {
        Page<Post> posts = postQueryRepository.searchByStatusRegionAndKeyword(status, region,
            keyword, pageable);

        List<Long> postIds = posts.getContent().stream()
            .map(Post::getId)
            .toList();

        Map<Long, Integer> commentCountMap =
            commentQueryRepository.countByPostIds(postIds, CommentStatus.ACTIVE);

        return posts.map(p -> {
            int commentCount = commentCountMap.getOrDefault(p.getId(), 0);
            return PostListItemResponse.from(p, commentCount);
        });
    }

    public PostResponse getDetail(Long id, boolean increaseView, Long currentUserId) {
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
        boolean isLiked =
            (currentUserId != null) && postLikeRepository.existsByPostIdAndUserId(post.getId(),
                currentUserId);

        // 댓글 수: ACTIVE (루트+대댓글 포함)
        Map<Long, Integer> commentCountMap = commentQueryRepository.countByPostIds(
            List.of(post.getId()), CommentStatus.ACTIVE);
        int commentCount = commentCountMap.getOrDefault(post.getId(), 0);

        // 베스트 댓글: ACTIVE, 좋아요 2개이상, 상위 3개
        List<Comment> best = commentRepository.findBestComments(post.getId(), 2, 3);
        List<CommentResponse> bestDtos = best.stream()
            .map(CommentResponse::from)
            .toList();
        log.info("[DETAIL] postId={}, commentCount={}, bestCount={}", post.getId(), commentCount,
            bestDtos.size());
        return PostResponse.from(post, isLiked, commentCount, bestDtos);
    }

    @Transactional(readOnly = true)
    public PostStatus findStatus(Long id) {
        Post post = postRepository.findById(id)
            .orElseThrow(() -> new CustomException(ErrorType.POST_NOT_FOUND));
        return post.getStatus();
    }

    @Transactional(readOnly = true)
    public Page<PostListItemResponse> search(String region, String keyword, Pageable pageable,
        Long currentUserId) {
        Page<Post> page = postQueryRepository.searchByRegionAndKeyword(region, keyword, pageable);

        List<Long> postIds = page.getContent().stream()
            .map(Post::getId)
            .toList();

        Map<Long, Integer> commentCountMap =
            commentQueryRepository.countByPostIds(postIds, CommentStatus.ACTIVE);

        if (currentUserId == null) {
            return page.map(
                p -> PostListItemResponse.from(p, commentCountMap.getOrDefault(p.getId(), 0)));
        }

        Set<Long> likedPostIds = postLikeRepository.findLikedPostIdsByUserIdAndPostIds(postIds,
            currentUserId);

        return page.map(p -> PostListItemResponse.from(
            p,
            likedPostIds.contains(p.getId()),
            commentCountMap.getOrDefault(p.getId(), 0)
        ));
    }

    @PreAuthorize("@authz.isPostOwner(#id) or hasRole('ADMIN')")
    public PostResponse update(Long id, PostUpdateRequest req, Long currentUserId) {
        requireActiveUser(currentUserId);
        Post post = postRepository.findById(id)
            .orElseThrow(() -> new CustomException(ErrorType.POST_NOT_FOUND));

        if (post.getStatus() == PostStatus.DELETED) {
            throw new CustomException(ErrorType.POST_NOT_FOUND);
        }

        if (req.title() != null) {
            post.changeTitle(req.title());
        }
        if (req.content() != null) {
            post.changeContent(req.content());
        }

        // region: null이면 "삭제 의도"로 보고 null 세팅(네가 이전에 그렇게 바꿔놨던 정책 유지)
        if (req.region() != null) {
            post.setRegion(req.region());
        } else {
            post.setRegion(null);
        }

        // 좌표: 둘 다 null이면 좌표 삭제, 둘 다 있으면 설정
        if (req.latitude() == null && req.longitude() == null) {
            post.setCoordinates(null, null);
        } else if (req.latitude() != null && req.longitude() != null) {
            post.setCoordinates(req.latitude(), req.longitude());
        }

        // tags: null이면 전체 삭제(빈 배열), 값 있으면 setTags
        if (req.tags() != null) {
            post.setTags(req.tags());
        } else {
            post.setTags(new ArrayList<>());
        }

        boolean isLiked =
            (currentUserId != null) && postLikeRepository.existsByPostIdAndUserId(post.getId(),
                currentUserId);
        return PostResponse.from(post, isLiked);
    }

    @PreAuthorize("isAuthenticated()")
    public LikeResponse toggleLike(Long postId, Long userId) {

        log.info("[LIKE] toggle start - postId={}, userId={}", postId, userId);

        // post 상태 체크(삭제/숨김 정책은 니 룰에 맞춰 조정 가능)
        Post post = postRepository.findById(postId)
            .orElseThrow(() -> new CustomException(ErrorType.POST_NOT_FOUND));

        if (post.getStatus() != PostStatus.ACTIVE) {
            throw new CustomException(ErrorType.POST_NOT_FOUND);
        }

        // user 존재/상태 체크
        User user = requireActiveUser(userId);

        Optional<PostLike> existingLike = postLikeRepository.findByPostIdAndUserId(postId, userId);

        if (existingLike.isPresent()) {
            // 좋아요 취소
            postLikeRepository.delete(existingLike.get());
            postRepository.decrementLikeCount(postId, PostStatus.ACTIVE);

            int likeCount = postRepository.findLikeCountById(postId)
                .orElseThrow(() -> new CustomException(ErrorType.POST_NOT_FOUND));

            log.info(
                "[LIKE] removed - postId={}, userId={}, likeCount={}",
                postId, userId, likeCount
            );

            return new LikeResponse(likeCount);
        }

        // 좋아요 추가
        PostLike newLike = new PostLike(post, user);
        postLikeRepository.save(newLike);
        postRepository.incrementLikeCount(postId, PostStatus.ACTIVE);

        int likeCount = postRepository.findLikeCountById(postId)
            .orElseThrow(() -> new CustomException(ErrorType.POST_NOT_FOUND));

        log.info(
            "[LIKE] added - postId={}, userId={}, likeCount={}",
            postId, userId, likeCount
        );

        return new LikeResponse(likeCount);
    }

    @PreAuthorize("@authz.isPostOwner(#id) or hasRole('ADMIN')")
    public void softDelete(Long id) {
        Post post = postRepository.findById(id)
            .orElseThrow(() -> new CustomException(ErrorType.POST_NOT_FOUND));
        post.softDelete();
    }

    @PreAuthorize("@authz.isPostOwner(#id) or hasRole('ADMIN')")
    public void hide(Long id) {
        Post post = postRepository.findById(id)
            .orElseThrow(() -> new CustomException(ErrorType.POST_NOT_FOUND));
        post.hide();
    }

    @PreAuthorize("@authz.isPostOwner(#id) or hasRole('ADMIN')")
    public void activate(Long id) {
        Post post = postRepository.findById(id)
            .orElseThrow(() -> new CustomException(ErrorType.POST_NOT_FOUND));
        post.activated();
    }

    private User requireActiveUser(Long userId) {
        if (userId == null) {
            throw new CustomException(ErrorType.COMMON_UNAUTHORIZED);
        }
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new CustomException(ErrorType.COMMON_UNAUTHORIZED));

        if (user.getStatus() == UserStatus.PENDING) {
            throw new CustomException(ErrorType.USER_EMAIL_NOT_VERIFIED);
        }
        if (user.getStatus() == UserStatus.BLOCKED) {
            throw new CustomException(ErrorType.USER_BLOCKED);
        }
        if (user.getStatus() == UserStatus.DELETED) {
            throw new CustomException(ErrorType.USER_NOT_FOUND);
        }
        return user;
    }
}
