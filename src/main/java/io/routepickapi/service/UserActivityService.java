package io.routepickapi.service;

import io.routepickapi.common.error.CustomException;
import io.routepickapi.common.error.ErrorType;
import io.routepickapi.dto.comment.MyCommentResponse;
import io.routepickapi.dto.post.PostListItemResponse;
import io.routepickapi.entity.comment.Comment;
import io.routepickapi.entity.comment.CommentStatus;
import io.routepickapi.entity.post.Post;
import io.routepickapi.entity.post.PostScrap;
import io.routepickapi.entity.post.PostStatus;
import io.routepickapi.entity.user.User;
import io.routepickapi.entity.user.UserStatus;
import io.routepickapi.repository.CommentQueryRepository;
import io.routepickapi.repository.CommentRepository;
import io.routepickapi.repository.PostLikeRepository;
import io.routepickapi.repository.PostRepository;
import io.routepickapi.repository.PostScrapRepository;
import io.routepickapi.repository.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserActivityService {

    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final PostLikeRepository postLikeRepository;
    private final PostScrapRepository postScrapRepository;
    private final CommentRepository commentRepository;
    private final CommentQueryRepository commentQueryRepository;

    public Page<PostListItemResponse> getMyPosts(Long userId, Pageable pageable,
        String statusFilter) {
        User user = requireActiveUser(userId);
        List<PostStatus> statuses = resolveStatuses(statusFilter);

        Page<Post> posts = postRepository.findByAuthorIdAndStatusIn(user.getId(), statuses,
            pageable);

        List<Long> postIds = posts.getContent().stream()
            .map(Post::getId)
            .toList();

        Map<Long, Integer> commentCountMap = commentQueryRepository.countByPostIds(postIds,
            CommentStatus.ACTIVE);
        Set<Long> likedPostIds = postIds.isEmpty()
            ? Set.of()
            : postLikeRepository.findLikedPostIdsByUserIdAndPostIds(postIds, user.getId());
        Set<Long> scrappedPostIds = postIds.isEmpty()
            ? Set.of()
            : postScrapRepository.findScrappedPostIdsByUserIdAndPostIds(postIds, user.getId());

        return posts.map(post -> PostListItemResponse.from(
            post,
            likedPostIds.contains(post.getId()),
            scrappedPostIds.contains(post.getId()),
            commentCountMap.getOrDefault(post.getId(), 0)
        ));
    }

    public Page<PostListItemResponse> getMyScraps(Long userId, Pageable pageable) {
        User user = requireActiveUser(userId);

        Page<PostScrap> scraps = postScrapRepository.findByUserIdAndPostStatusOrderByCreatedAtDesc(
            user.getId(), PostStatus.ACTIVE, pageable);

        List<Long> postIds = scraps.getContent().stream()
            .map(scrap -> scrap.getPost().getId())
            .toList();

        Map<Long, Integer> commentCountMap = commentQueryRepository.countByPostIds(postIds,
            CommentStatus.ACTIVE);
        Set<Long> likedPostIds = postIds.isEmpty()
            ? Set.of()
            : postLikeRepository.findLikedPostIdsByUserIdAndPostIds(postIds, user.getId());

        return scraps.map(scrap -> PostListItemResponse.from(
            scrap.getPost(),
            likedPostIds.contains(scrap.getPost().getId()),
            true,
            commentCountMap.getOrDefault(scrap.getPost().getId(), 0)
        ));
    }

    public Page<MyCommentResponse> getMyComments(Long userId, Pageable pageable) {
        User user = requireActiveUser(userId);

        Page<Comment> comments = commentRepository.findByAuthorIdAndStatus(user.getId(),
            CommentStatus.ACTIVE, pageable);
        return comments.map(MyCommentResponse::from);
    }

    private List<PostStatus> resolveStatuses(String statusFilter) {
        if (statusFilter == null || statusFilter.isBlank()) {
            return List.of(PostStatus.ACTIVE, PostStatus.HIDDEN);
        }

        String normalized = statusFilter.trim().toUpperCase();
        if ("ACTIVE".equals(normalized)) {
            return List.of(PostStatus.ACTIVE);
        }

        if ("HIDDEN".equals(normalized)) {
            return List.of(PostStatus.HIDDEN);
        }

        return List.of(PostStatus.ACTIVE, PostStatus.HIDDEN);
    }

    private User requireActiveUser(Long userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new CustomException(ErrorType.USER_NOT_FOUND));

        if (user.getStatus() == UserStatus.BLOCKED) {
            throw new CustomException(ErrorType.USER_BLOCKED);
        }

        if (user.getStatus() == UserStatus.DELETED) {
            throw new CustomException(ErrorType.USER_NOT_FOUND);
        }

        return user;
    }
}
