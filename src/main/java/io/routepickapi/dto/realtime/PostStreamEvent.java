package io.routepickapi.dto.realtime;

import java.time.LocalDateTime;

public record PostStreamEvent(
    Long postId,
    String title,
    Long authorId,
    String authorNickname,
    LocalDateTime createdAt
) {
}
