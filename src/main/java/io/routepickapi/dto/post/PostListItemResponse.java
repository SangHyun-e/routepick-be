package io.routepickapi.dto.post;

import io.routepickapi.entity.post.Post;
import io.routepickapi.entity.post.PostStatus;
import java.time.LocalDateTime;
import java.util.List;

public record PostListItemResponse(
    Long id,
    String title,
    String region,
    PostStatus status,
    int likeCount,
    int viewCount,
    LocalDateTime createdAt
) {
    public static PostListItemResponse from(Post p) {
        return new PostListItemResponse(
            p.getId(),
            p.getTitle(),
            p.getRegion(),
            p.getStatus(),
            p.getLikeCount(),
            p.getViewCount(),
            p.getCreatedAt()
        );
    }
}
