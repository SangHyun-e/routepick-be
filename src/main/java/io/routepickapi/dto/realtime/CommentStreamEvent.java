package io.routepickapi.dto.realtime;

import java.time.LocalDateTime;

public record CommentStreamEvent(
    Long postId,
    Long commentId,
    Long authorId,
    String authorNickname,
    LocalDateTime createdAt
) {
}
