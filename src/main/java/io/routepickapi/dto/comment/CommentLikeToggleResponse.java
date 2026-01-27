package io.routepickapi.dto.comment;

public record CommentLikeToggleResponse(
    Long commentId,
    int likeCount,
    boolean liked
) {

}
