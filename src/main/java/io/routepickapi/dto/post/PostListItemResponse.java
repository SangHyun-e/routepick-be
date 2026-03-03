package io.routepickapi.dto.post;

import io.routepickapi.entity.post.Post;
import io.routepickapi.entity.post.PostStatus;
import io.routepickapi.entity.user.User;
import java.time.LocalDateTime;

public record PostListItemResponse(
    Long id,
    String title,
    String region,
    PostStatus status,
    boolean isNotice,
    boolean noticePinned,
    int likeCount,
    int viewCount,
    LocalDateTime createdAt,
    Long authorId,
    String authorNickname,
    Boolean isLikedByCurrentUser,
    Boolean isScrappedByCurrentUser,
    int commentCount

) {

    public static PostListItemResponse from(Post p, int commentCount) {
        User a = p.getAuthor();
        return new PostListItemResponse(
            p.getId(),
            p.getTitle(),
            p.getRegion(),
            p.getStatus(),
            p.isNotice(),
            p.isNoticePinned(),
            p.getLikeCount(),
            p.getViewCount(),
            p.getCreatedAt(),
            a != null ? a.getId() : null,
            a != null ? a.getNickname() : null,
            null,
            null,
            commentCount
        );
    }

    public static PostListItemResponse from(Post p, Boolean isLikedByCurrentUser,
        Boolean isScrappedByCurrentUser, int commentCount) {
        User a = p.getAuthor();
        return new PostListItemResponse(
            p.getId(),
            p.getTitle(),
            p.getRegion(),
            p.getStatus(),
            p.isNotice(),
            p.isNoticePinned(),
            p.getLikeCount(),
            p.getViewCount(),
            p.getCreatedAt(),
            a != null ? a.getId() : null,
            a != null ? a.getNickname() : null,
            isLikedByCurrentUser,
            isScrappedByCurrentUser,
            commentCount
        );
    }
}
