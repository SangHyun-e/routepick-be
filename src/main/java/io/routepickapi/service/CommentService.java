package io.routepickapi.service;

import io.routepickapi.common.error.CustomException;
import io.routepickapi.common.error.ErrorType;
import io.routepickapi.dto.comment.CommentCreateRequest;
import io.routepickapi.dto.comment.CommentResponse;
import io.routepickapi.dto.comment.CommentUpdateRequest;
import io.routepickapi.entity.comment.Comment;
import io.routepickapi.entity.comment.CommentStatus;
import io.routepickapi.entity.post.Post;
import io.routepickapi.entity.post.PostStatus;
import io.routepickapi.entity.user.User;
import io.routepickapi.entity.user.UserStatus;
import io.routepickapi.repository.CommentRepository;
import io.routepickapi.repository.PostRepository;
import io.routepickapi.repository.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
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
public class CommentService {

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;

    public Long createRoot(Long postId, Long currentUserId, CommentCreateRequest req) {
        Post post = postRepository.findByIdAndStatus(postId, PostStatus.ACTIVE)
            .orElseThrow(
                () -> new CustomException(ErrorType.POST_NOT_FOUND));

        Comment comment = Comment.builder()
            .post(post)
            .parent(null)
            .content(req.content())
            .build();

        if (currentUserId != null) {
            User author = userRepository.findByIdAndStatus(currentUserId, UserStatus.ACTIVE)
                .orElseThrow(
                    () -> new CustomException(ErrorType.COMMON_UNAUTHORIZED));
            comment.setAuthor(author);
        }

        Long id = commentRepository.save(comment).getId();
        log.info("Create root comment: postId={}, commentId={}, authorId={}", postId, id,
            comment.getAuthor() != null ? comment.getAuthor().getId() : null);
        return id;
    }

    public Long createReply(Long postId, Long parentId, Long currentUserId,
        CommentCreateRequest req) {
        Post post = postRepository.findByIdAndStatus(postId, PostStatus.ACTIVE)
            .orElseThrow(
                () -> new CustomException(ErrorType.POST_NOT_FOUND));

        Comment parent = commentRepository.findById(parentId)
            .orElseThrow(() -> new CustomException(ErrorType.COMMENT_NOT_FOUND));

        // 1) 부모 댓글이 같은 게시글 소속인지 검사 (cross-post 방지)
        if (!parent.getPost().getId().equals(postId)) {
            throw new CustomException(ErrorType.COMMENT_PARENT_MISMATCH);
        }

        // 2) 부모 상태 검사 (비활성/삭제된 부모에 대댓글 금지)
        if (parent.getStatus() != CommentStatus.ACTIVE) {
            throw new CustomException(ErrorType.COMMENT_PARENT_NOT_ACTIVE);
        }

        Comment reply = Comment.builder()
            .post(post)
            .parent(parent)
            .content(req.content())
            .build();

        if (currentUserId != null) {
            User author = userRepository.findByIdAndStatus(currentUserId, UserStatus.ACTIVE)
                .orElseThrow(
                    () -> new CustomException(ErrorType.COMMON_UNAUTHORIZED));
            reply.setAuthor(author);
        }

        Long id = commentRepository.save(reply).getId();
        log.info("Create reply: postId={}, parentId={}, commentId={}, authorId={}", postId,
            parentId, id, reply.getAuthor() != null ? reply.getAuthor().getId() : null);
        return id;
    }

    @Transactional(readOnly = true)
    public Page<CommentResponse> listRootsWithReplies(Long postId, Pageable pageable) {
        // 1) 루트 페이지 (QueryDSL 교체)
        Page<Comment> roots = commentRepository.findRootsForList(postId, pageable);

        // 2) 부모댓글 ID 수집
        List<Long> parentIds = roots.getContent().stream()
            .map(Comment::getId)
            .toList();

        // 3) 대댓글 일괄 조회(작성시간 오름차순, QueryDSL 교체)
        List<Comment> replies = parentIds.isEmpty()
            ? List.of()
            : commentRepository.findActiveReplies(parentIds);

        // 4) parentId -> children 맵핑
        Map<Long, List<Comment>> childrenMap = replies.stream()
            .collect(Collectors.groupingBy(c -> c.getParent().getId()));

        // 5) DTO 변환 (root + children)
        return roots.map(root ->
            CommentResponse.fromWithChildren(
                root,
                childrenMap.getOrDefault(root.getId(), List.of())
            )
        );
    }

    @Transactional
    public int like(Long postId, Long commentId) {
        // 원자적 like_count + 1
        log.debug("Request like: postId={}, commentId={}", postId, commentId);
        int updated = commentRepository.incrementLikeCount(postId, commentId, CommentStatus.ACTIVE);

        if (updated == 0) {
            log.warn("Like failed: not found or inactive (postId={}, commentId={})", postId,
                commentId);
            throw new CustomException(ErrorType.COMMENT_NOT_FOUND);
        }

        // 갱신된 카운트 조회해서 반환
        int likeCount = commentRepository
            .findByIdAndPostIdAndStatus(commentId, postId, CommentStatus.ACTIVE)
            .map(Comment::getLikeCount)
            .orElseThrow(
                () -> {
                    log.error("Like succeed but reload failed (postId={}, commentId={})", postId,
                        commentId);
                    return new CustomException(ErrorType.COMMENT_NOT_FOUND);
                });
        log.info("Like increased: postId={}, commentId={}, likeCount={}", postId, commentId,
            likeCount);
        return likeCount;
    }

    @Transactional
    @PreAuthorize("@authz.isCommentOwner(#postId, #commentId) or hasRole('ADMIN')")
    public void softDelete(Long postId, Long commentId) {
        log.debug("Delete comment request: postId={}, commentId={}", postId, commentId);

        int updated = commentRepository.updateStatus(
            postId, commentId, CommentStatus.ACTIVE, CommentStatus.DELETED
        );

        if (updated == 0) {
            throw new CustomException(ErrorType.COMMENT_NOT_FOUND);
        }
        log.info("Comment soft-deleted: postId={}, commentId={}", postId, commentId);
    }

    @Transactional
    @PreAuthorize("@authz.isCommentOwner(#postId, #commentId) or hasRole('ADMIN')")
    public CommentResponse updateContent(Long postId, Long commentId, CommentUpdateRequest req) {
        // ACTIVE 인 대상만 수정 허용
        Comment c = commentRepository
            .findByIdAndPostIdAndStatus(commentId, postId, CommentStatus.ACTIVE)
            .orElseThrow(() -> new CustomException(
                ErrorType.COMMENT_NOT_FOUND));

        c.changeContent(req.content()); // 엔티티 유효성 검증 포함(<=1000, not blank)

        log.info("Comment updated: postId={}, commentId={}", postId, commentId);
        return CommentResponse.from(c); // DTO 마스킹 규칙 적용
    }


}
