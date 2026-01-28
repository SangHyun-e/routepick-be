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

    private static boolean isDelete(Comment c) {
        return c.getStatus() == CommentStatus.DELETED;
    }

    private static String toContent(Comment c) {
        return isDelete(c) ? "(삭제된 댓글입니다)" : c.getContent();
    }

    private static Long toAuthorId(Comment c) {
        if (isDelete(c)) {
            return null;
        }
        User a = c.getAuthor();
        return (a != null) ? a.getId() : null;
    }

    private static String toAuthorNickname(Comment c) {
        if (isDelete(c)) {
            return "알 수 없음";
        }
        User a = c.getAuthor();
        return (a != null) ? a.getNickname() : "익명";
    }

    private static Long toParentId(Comment c) {
        return (c.getParent() == null) ? null : c.getParent().getId();
    }

    public static CommentResponse from(Comment c) {
        return new CommentResponse(
            c.getId(),
            toParentId(c),
            c.getDepth(),
            toContent(c),
            c.getLikeCount(),
            c.getStatus(),
            c.getCreatedAt(),
            toAuthorId(c),
            toAuthorNickname(c),
            new ArrayList<>()
        );
    }

    public static CommentResponse fromWithChildren(Comment root, List<Comment> children) {
        List<CommentResponse> childDtos = new ArrayList<>();
        for (Comment child : children) {
            childDtos.add(CommentResponse.from(child)); // ← 자식도 동일 마스킹 규칙 적용
        }

        return new CommentResponse(
            root.getId(),
            toParentId(root),
            root.getDepth(),
            toContent(root),
            root.getLikeCount(),
            root.getStatus(),
            root.getCreatedAt(),
            toAuthorId(root),
            toAuthorNickname(root),
            childDtos
        );
    }
}