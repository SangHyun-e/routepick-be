package io.routepickapi.service;

import io.routepickapi.common.error.CustomException;
import io.routepickapi.common.error.ErrorType;
import io.routepickapi.dto.comment.CommentCreateRequest;
import io.routepickapi.dto.comment.CommentLikeToggleResponse;
import io.routepickapi.dto.comment.CommentResponse;
import io.routepickapi.dto.comment.CommentUpdateRequest;
import io.routepickapi.entity.comment.Comment;
import io.routepickapi.entity.comment.CommentDeletedBy;
import io.routepickapi.entity.comment.CommentLike;
import io.routepickapi.entity.comment.CommentStatus;
import io.routepickapi.entity.notification.NotificationResourceType;
import io.routepickapi.entity.notification.NotificationType;
import io.routepickapi.entity.post.Post;
import io.routepickapi.entity.post.PostStatus;
import io.routepickapi.entity.user.User;
import io.routepickapi.entity.user.UserStatus;
import io.routepickapi.repository.CommentLikeRepository;
import io.routepickapi.repository.CommentRepository;
import io.routepickapi.repository.PostRepository;
import io.routepickapi.repository.UserRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
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
    private final CommentLikeRepository commentLikeRepository;
    private final NotificationService notificationService;

    private static final Pattern MENTION_PATTERN = Pattern.compile("@[\\w가-힣]+");

    public Long createRoot(Long postId, Long currentUserId, CommentCreateRequest req) {
        Post post = postRepository.findByIdAndStatus(postId, PostStatus.ACTIVE)
            .orElseThrow(
                () -> new CustomException(ErrorType.POST_NOT_FOUND));
        validateNoticeComment(post);

        Comment comment = Comment.builder()
            .post(post)
            .parent(null)
            .content(req.content())
            .build();

        if (currentUserId != null) {
            User author = requireActiveUser(currentUserId);
            comment.setAuthor(author);
        }

        Long id = commentRepository.save(comment).getId();
        notifyComment(post, comment, comment.getAuthor(), null);
        log.info("Create root comment: postId={}, commentId={}, authorId={}", postId, id,
            comment.getAuthor() != null ? comment.getAuthor().getId() : null);
        return id;
    }

    public Long createReply(Long postId, Long parentId, Long currentUserId,
        CommentCreateRequest req) {
        Post post = postRepository.findByIdAndStatus(postId, PostStatus.ACTIVE)
            .orElseThrow(
                () -> new CustomException(ErrorType.POST_NOT_FOUND));
        validateNoticeComment(post);

        Comment parent = commentRepository.findById(parentId)
            .orElseThrow(() -> new CustomException(ErrorType.COMMENT_NOT_FOUND));
        Comment replyTarget = parent;

        // 1) 부모 댓글이 같은 게시글 소속인지 검사 (cross-post 방지)
        if (!parent.getPost().getId().equals(postId)) {
            throw new CustomException(ErrorType.COMMENT_PARENT_MISMATCH);
        }

        // 2) 부모 상태 검사 (비활성/삭제된 부모에 대댓글 금지)
        if (parent.getStatus() != CommentStatus.ACTIVE) {
            throw new CustomException(ErrorType.COMMENT_PARENT_NOT_ACTIVE);
        }

        Comment replyParent = resolveRootParent(parent);
        Comment reply = Comment.builder()
            .post(post)
            .parent(replyParent)
            .content(req.content())
            .build();

        if (currentUserId != null) {
            User author = requireActiveUser(currentUserId);
            reply.setAuthor(author);
        }

        Long id = commentRepository.save(reply).getId();
        notifyComment(post, reply, reply.getAuthor(), replyTarget);
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
            : commentRepository.findRepliesForList(parentIds);

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
    public CommentLikeToggleResponse toggleLike(Long postId, Long commentId, Long currentUserId) {
        User user = requireActiveUser(currentUserId);

        Comment comment = commentRepository.findByIdAndPostIdAndStatus(
                commentId, postId, CommentStatus.ACTIVE
            )
            .orElseThrow(() -> new CustomException(ErrorType.COMMENT_NOT_FOUND));
        validateNoticeLike(comment.getPost());

        return commentLikeRepository.findByCommentAndUser(comment, user)
            .map(existing -> {
                // 좋아요 취소
                commentLikeRepository.delete(existing);

                int updated = commentRepository.decrementLikeCount(
                    postId, commentId, CommentStatus.ACTIVE
                );
                if (updated == 0) {
                    throw new CustomException(ErrorType.COMMENT_NOT_FOUND);
                }

                int likeCount = commentRepository
                    .findByIdAndPostIdAndStatus(commentId, postId, CommentStatus.ACTIVE)
                    .map(Comment::getLikeCount)
                    .orElseThrow(() -> new CustomException(ErrorType.COMMENT_NOT_FOUND));

                log.info("Comment like OFF: postId={}, commentId={}, userId={}, likeCount={}",
                    postId, commentId, currentUserId, likeCount);

                return new CommentLikeToggleResponse(commentId, likeCount, false);
            })
            .orElseGet(() -> {
                // 좋아요 추가
                try {
                    commentLikeRepository.save(new CommentLike(comment, user));
                } catch (DataIntegrityViolationException e) {
                    log.warn("Duplicate like insert (race): postId={}, commentId-{}, userId={}",
                        postId, commentId, currentUserId);
                }

                int updated = commentRepository.incrementLikeCount(
                    postId, commentId, CommentStatus.ACTIVE
                );
                if (updated == 0) {
                    throw new CustomException(ErrorType.COMMENT_NOT_FOUND);
                }

                int likeCount = commentRepository
                    .findByIdAndPostIdAndStatus(commentId, postId, CommentStatus.ACTIVE)
                    .map(Comment::getLikeCount)
                    .orElseThrow(() -> new CustomException(ErrorType.COMMENT_NOT_FOUND));

                log.info("Comment like ON: postId={}, commentId={}, userId={}, likeCount={}",
                    postId, commentId, currentUserId, likeCount);

                return new CommentLikeToggleResponse(commentId, likeCount, true);
            });
    }


    @Transactional
    @PreAuthorize("@authz.isCommentOwner(#postId, #commentId) or hasRole('ADMIN')")
    public void softDelete(Long postId, Long commentId, CommentDeletedBy deletedBy) {
        log.debug("Delete comment request: postId={}, commentId={}", postId, commentId);
        Comment comment = commentRepository.findByIdAndPostIdAndStatus(
                commentId, postId, CommentStatus.ACTIVE
            )
            .orElseThrow(() -> new CustomException(ErrorType.COMMENT_NOT_FOUND));
        validateNoticeComment(comment.getPost());

        comment.softDelete(deletedBy != null ? deletedBy : CommentDeletedBy.USER);
        log.info("Comment soft-deleted: postId={}, commentId={}, deletedBy={}", postId, commentId,
            comment.getDeletedBy());
    }

    @Transactional
    @PreAuthorize("@authz.isCommentOwner(#postId, #commentId) or hasRole('ADMIN')")
    public CommentResponse updateContent(Long postId, Long commentId, CommentUpdateRequest req) {
        // ACTIVE 인 대상만 수정 허용
        Comment c = commentRepository
            .findByIdAndPostIdAndStatus(commentId, postId, CommentStatus.ACTIVE)
            .orElseThrow(() -> new CustomException(
                ErrorType.COMMENT_NOT_FOUND));
        validateNoticeComment(c.getPost());

        c.changeContent(req.content()); // 엔티티 유효성 검증 포함(<=1000, not blank)

        log.info("Comment updated: postId={}, commentId={}", postId, commentId);
        return CommentResponse.from(c); // DTO 마스킹 규칙 적용
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

    private void notifyComment(Post post, Comment comment, User actor, Comment parent) {
        if (post == null || comment == null || actor == null) {
            return;
        }

        Long actorId = actor.getId();
        String actorNickname = actor.getNickname();
        Set<Long> notifiedUserIds = new HashSet<>();

        User postAuthor = post.getAuthor();
        if (postAuthor != null && !postAuthor.getId().equals(actorId)) {
            String title = parent == null ? "새 댓글이 달렸어요" : "내 글에 새 댓글이 달렸어요";
            String message = String.format("'%s' 글에 새 댓글이 등록됐어요.", post.getTitle());
            notificationService.createNotification(
                postAuthor,
                NotificationType.COMMENT,
                title,
                message,
                NotificationResourceType.POST,
                post.getId(),
                actorId,
                actorNickname,
                null
            );
            notifiedUserIds.add(postAuthor.getId());
        }

        if (parent != null) {
            User parentAuthor = parent.getAuthor();
            if (parentAuthor != null
                && !parentAuthor.getId().equals(actorId)
                && !notifiedUserIds.contains(parentAuthor.getId())) {
                notificationService.createNotification(
                    parentAuthor,
                    NotificationType.REPLY,
                    "내 댓글에 답글이 달렸어요",
                    String.format("'%s' 글에서 내 댓글에 답글이 달렸어요.", post.getTitle()),
                    NotificationResourceType.POST,
                    post.getId(),
                    actorId,
                    actorNickname,
                    null
                );
                notifiedUserIds.add(parentAuthor.getId());
            }
        }

        Set<User> mentionUsers = resolveMentionUsers(comment.getContent());
        for (User mentionUser : mentionUsers) {
            if (mentionUser.getId().equals(actorId)) {
                continue;
            }
            if (notifiedUserIds.contains(mentionUser.getId())) {
                continue;
            }
            notificationService.createNotification(
                mentionUser,
                NotificationType.MENTION,
                "댓글에서 언급되었어요",
                String.format("'%s' 글에서 %s님이 회원님을 언급했어요.", post.getTitle(),
                    actorNickname),
                NotificationResourceType.POST,
                post.getId(),
                actorId,
                actorNickname,
                null
            );
        }
    }

    private Set<User> resolveMentionUsers(String content) {
        if (content == null || content.isBlank()) {
            return Set.of();
        }
        Set<String> nicknames = new HashSet<>();
        Matcher matcher = MENTION_PATTERN.matcher(content);
        while (matcher.find()) {
            String mention = matcher.group();
            if (mention.length() <= 1) {
                continue;
            }
            String nickname = mention.substring(1).trim();
            if (!nickname.isBlank()) {
                nicknames.add(nickname);
            }
        }
        if (nicknames.isEmpty()) {
            return Set.of();
        }
        Set<User> users = new HashSet<>();
        for (String nickname : nicknames) {
            userRepository.findByNickname(nickname)
                .filter(user -> user.getStatus() != UserStatus.DELETED)
                .ifPresent(users::add);
        }
        return users;
    }

    private Comment resolveRootParent(Comment parent) {
        Comment current = parent;
        while (current.getParent() != null) {
            current = current.getParent();
        }
        return current;
    }

    private void validateNoticeComment(Post post) {
        if (post.isNotice()) {
            throw new CustomException(ErrorType.POST_NOTICE_COMMENT_NOT_ALLOWED);
        }
    }

    private void validateNoticeLike(Post post) {
        if (post.isNotice()) {
            throw new CustomException(ErrorType.POST_NOTICE_LIKE_NOT_ALLOWED);
        }
    }


}
