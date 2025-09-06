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
    int likeCount,
    int viewCount,
    LocalDateTime createdAt,
    Long authorId,
    String authorNickname
) {

    public static PostListItemResponse from(Post p) {
        User a = p.getAuthor();
        return new PostListItemResponse(
            p.getId(),
            p.getTitle(),
            p.getRegion(),
            p.getStatus(),
            p.getLikeCount(),
            p.getViewCount(),
            p.getCreatedAt(),
            a != null ? a.getId() : null,
            a != null ? a.getNickname() : null
        );
    }
}
