package io.routepickapi.dto.comment;

import io.routepickapi.entity.comment.Comment;
import io.routepickapi.entity.comment.CommentStatus;
import io.routepickapi.entity.user.User;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public record CommentResponse(
    Long id,
    Long parentId,
    int depth,
    String content,
    int likeCount,
    CommentStatus status,
    LocalDateTime createdAt,
    Long authorId,
    String authorNickname,
    List<CommentResponse> replies
) {

    private static String maskIfDeleted(Comment c) {
        return (c.getStatus() == CommentStatus.DELETED)
            ? "(삭제된 댓글입니다)"
            : c.getContent();
    }

    private static Long toAuthorId(Comment c) {
        User a = c.getAuthor();
        return (a != null) ? a.getId() : null;
    }

    private static String toAuthorNickname(Comment c) {
        User a = c.getAuthor();
        return (a != null) ? a.getNickname() : null;
    }

    public static CommentResponse from(Comment c) {
        Long pId = (c.getParent() == null) ? null : c.getParent().getId();
        return new CommentResponse(
            c.getId(),
            pId,
            c.getDepth(),
            maskIfDeleted(c),
            c.getLikeCount(),
            c.getStatus(),
            c.getCreatedAt(),
            toAuthorId(c),
            toAuthorNickname(c),
            new ArrayList<>() // 기본은 빈 리스트
        );
    }

    public static CommentResponse fromWithChildren(Comment root, List<Comment> children) {
        List<CommentResponse> childDtos = new ArrayList<>();
        for (Comment child : children) {
            childDtos.add(CommentResponse.from(child));
        }
        Long pId = (root.getParent() == null) ? null : root.getParent().getId();
        return new CommentResponse(
            root.getId(),
            pId,
            root.getDepth(),
            maskIfDeleted(root),
            root.getLikeCount(),
            root.getStatus(),
            root.getCreatedAt(),
            toAuthorId(root),
            toAuthorNickname(root),
            childDtos
        );
    }
}
