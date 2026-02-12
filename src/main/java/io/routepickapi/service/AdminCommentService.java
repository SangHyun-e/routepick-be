package io.routepickapi.service;

import io.routepickapi.common.error.CustomException;
import io.routepickapi.common.error.ErrorType;
import io.routepickapi.dto.comment.AdminCommentListItemResponse;
import io.routepickapi.entity.comment.Comment;
import io.routepickapi.entity.comment.CommentDeletedBy;
import io.routepickapi.entity.comment.CommentStatus;
import io.routepickapi.repository.CommentRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class AdminCommentService {

    private final CommentRepository commentRepository;

    @Transactional(readOnly = true)
    public Page<AdminCommentListItemResponse> list(String status, String keyword,
        Pageable pageable) {
        List<CommentStatus> statuses = resolveStatuses(status);
        Page<Comment> page = commentRepository.findForAdmin(statuses, keyword, pageable);
        return page.map(AdminCommentListItemResponse::from);
    }

    public void softDelete(Long commentId) {
        Comment comment = commentRepository.findById(commentId)
            .orElseThrow(() -> new CustomException(ErrorType.COMMENT_NOT_FOUND));

        if (comment.getStatus() == CommentStatus.DELETED) {
            return;
        }

        comment.softDelete(CommentDeletedBy.ADMIN);
    }

    public void activate(Long commentId) {
        Comment comment = commentRepository.findById(commentId)
            .orElseThrow(() -> new CustomException(ErrorType.COMMENT_NOT_FOUND));

        if (comment.getStatus() == CommentStatus.ACTIVE) {
            return;
        }

        comment.activate();
    }

    public void hardDelete(Long commentId) {
        Comment comment = commentRepository.findById(commentId)
            .orElseThrow(() -> new CustomException(ErrorType.COMMENT_NOT_FOUND));
        commentRepository.delete(comment);
    }

    private List<CommentStatus> resolveStatuses(String status) {
        if (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)) {
            return List.of(CommentStatus.ACTIVE, CommentStatus.DELETED);
        }

        if ("ACTIVE".equalsIgnoreCase(status)) {
            return List.of(CommentStatus.ACTIVE);
        }

        if ("DELETED".equalsIgnoreCase(status)) {
            return List.of(CommentStatus.DELETED);
        }

        return List.of(CommentStatus.ACTIVE, CommentStatus.DELETED);
    }
}
