package io.routepickapi.dto.comment;

import io.routepickapi.entity.comment.Comment;
import io.routepickapi.entity.comment.CommentDeletedBy;
import io.routepickapi.entity.comment.CommentStatus;
import io.routepickapi.entity.user.User;
import java.time.LocalDateTime;

public record AdminCommentListItemResponse(
    Long id,
    Long postId,
    String postTitle,
    Long parentId,
    int depth,
    String content,
    CommentStatus status,
    CommentDeletedBy deletedBy,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    Long authorId,
    String authorNickname
) {

    public static AdminCommentListItemResponse from(Comment comment) {
        User author = comment.getAuthor();
        return new AdminCommentListItemResponse(
            comment.getId(),
            comment.getPost().getId(),
            comment.getPost().getTitle(),
            comment.getParent() != null ? comment.getParent().getId() : null,
            comment.getDepth(),
            comment.getContent(),
            comment.getStatus(),
            comment.getDeletedBy(),
            comment.getCreatedAt(),
            comment.getUpdatedAt(),
            author != null ? author.getId() : null,
            author != null ? author.getNickname() : null
        );
    }
}
