package io.routepickapi.dto.post;

import io.routepickapi.entity.post.Post;
import io.routepickapi.entity.post.PostStatus;
import io.routepickapi.entity.user.User;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public record PostResponse(
    Long id,
    String title,
    String content,
    Double latitude,
    Double longitude,
    String region,
    List<String> tags,
    int likeCount,
    int viewCount,
    PostStatus status,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    String createdBy,
    String updatedBy,
    Long authorId,
    String authorNickname
) {

    public static PostResponse from(Post p) {
        List<String> safeTags = new ArrayList<>(p.getTags());
        User a = p.getAuthor();
        return new PostResponse(
            p.getId(),
            p.getTitle(),
            p.getContent(),
            p.getLatitude(),
            p.getLongitude(),
            p.getRegion(),
            safeTags,
            p.getLikeCount(),
            p.getViewCount(),
            p.getStatus(),
            p.getCreatedAt(),
            p.getUpdatedAt(),
            p.getCreatedBy(),
            p.getUpdatedBy(),
            a != null ? a.getId() : null,
            a != null ? a.getNickname() : null
        );
    }
}
