package io.routepickapi.dto.comment;

import io.routepickapi.entity.comment.Comment;
import io.routepickapi.entity.comment.CommentStatus;
import java.time.LocalDateTime;

public record MyCommentResponse(
    Long id,
    Long postId,
    String postTitle,
    String content,
    CommentStatus status,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {

    private static String toContent(Comment comment) {
        if (comment.getStatus() == CommentStatus.DELETED) {
            return "(삭제된 댓글입니다)";
        }
        return comment.getContent();
    }

    public static MyCommentResponse from(Comment comment) {
        return new MyCommentResponse(
            comment.getId(),
            comment.getPost().getId(),
            comment.getPost().getTitle(),
            toContent(comment),
            comment.getStatus(),
            comment.getCreatedAt(),
            comment.getUpdatedAt()
        );
    }
}
